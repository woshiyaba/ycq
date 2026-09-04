const config = require('../config/api.js');
const util = require('../utils/util.js');
let socket = null;
let connected = false;
let pending = null;
let unread = 0;
let listeners = [];
const awaiting = Object.create(null);

function settleSend(id, error, message) {
  const entry = awaiting[id];
  if (!entry) return;
  clearTimeout(entry.timer);
  delete awaiting[id];
  if (error) entry.reject(error);
  else entry.resolve(message);
}

function rejectSends(task, error) {
  Object.keys(awaiting).forEach(id => {
    if (awaiting[id].task === task) settleSend(id, error);
  });
}

function setBadge(value) {
  unread = Math.max(0, Number(value) || 0);
  getApp().globalData.unreadCount = unread;
  const pages = getCurrentPages();
  const page = pages[pages.length - 1];
  const tab = page && typeof page.getTabBar === 'function' && page.getTabBar();
  if (tab) tab.setData({
    unread
  });
}

function wsConnect() {
  const user = wx.getStorageSync('userInfo');
  if (!user || !user.openId || !wx.getStorageSync('token')) return Promise.reject(new Error('请先登录'));
  if (connected && socket) return Promise.resolve(socket);
  if (pending) return pending;
  const attempt = new Promise((resolve, reject) => {
    const task = wx.connectSocket({
      url: config.ChatWs + '/' + encodeURIComponent(user.openId),
      header: {
        Authorization: wx.getStorageSync('token')
      },
      fail: reject
    });
    socket = task;
    task.onOpen(() => {
      if (socket !== task) {
        reject(new Error('连接已关闭'));
        return;
      }
      connected = true;
      pending = null;
      resolve(task);
    });
    task.onClose(() => {
      rejectSends(task, new Error('消息连接已关闭'));
      if (socket === task) {
        connected = false;
        pending = null;
        socket = null;
      }
      reject(new Error('消息连接已关闭'));
    });
    task.onError(() => {
      rejectSends(task, new Error('消息连接失败'));
      if (socket === task) {
        connected = false;
        pending = null;
        socket = null;
      }
      reject(new Error('消息连接失败'));
    });
    task.onMessage(event => {
      if (socket !== task) return;
      let res;
      try {
        res = JSON.parse(event.data);
      } catch (error) {
        return;
      }
      const message = res.data || {};
      if (res.errno !== 0) {
        if (message.clientMessageId) settleSend(message.clientMessageId, new Error(res.errmsg ||
          '消息发送失败'));
        return;
      }
      if (message.messageType === 5) {
        settleSend(message.clientMessageId, null, message);
        return;
      }
      if (!res.data) return;
      if (message.messageType === 4) setBadge(message.messageBody);
      else if (message.messageType === 1 || message.messageType === 3) setBadge(unread + 1);
      listeners.slice().forEach(listener => listener(message));
    });
  });
  pending = attempt;
  attempt.catch(() => {
    if (pending === attempt) pending = null;
  });
  return attempt;
}

function wsClose() {
  const task = socket;
  rejectSends(task, new Error('消息连接已关闭'));
  socket = null;
  connected = false;
  pending = null;
  if (task) task.close();
}

function subscribe(listener) {
  listeners.push(listener);
  return () => {
    listeners = listeners.filter(item => item !== listener);
  };
}

function sendMessage(data) {
  let message;
  try {
    message = typeof data === 'string' ? JSON.parse(data) : Object.assign({}, data);
    if (!message.receiverId) throw new Error('聊天信息尚未加载完成');
    if (typeof message.messageBody !== 'string' || !message.messageBody.trim() || message.messageBody.length >
      2000) {
      throw new Error('消息须为1至2000字');
    }
  } catch (error) {
    return Promise.reject(error);
  }
  const id = util.id();
  message.clientMessageId = id;
  return wsConnect().then(task => new Promise((resolve, reject) => {
    if (socket !== task || !connected) {
      reject(new Error('消息连接已关闭'));
      return;
    }
    awaiting[id] = {
      task,
      resolve,
      reject,
      timer: setTimeout(() => settleSend(id, new Error('发送状态未确认，请刷新会话后重试')), 15000)
    };
    try {
      task.send({
        data: JSON.stringify(message),
        fail: error => settleSend(id, new Error(error.errMsg || error.message || '消息发送失败'))
      });
    } catch (error) {
      settleSend(id, error);
    }
  }));
}

function lessBadge(count) {
  setBadge(unread - count);
}
module.exports = {
  wsConnect,
  wsClose,
  subscribe,
  sendMessage,
  lessBadge,
  setBadge
};
