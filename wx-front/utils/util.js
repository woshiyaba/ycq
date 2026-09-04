const config = require('../config/api.js');
let loginPending;

function formatTime(date) {
  const pad = n => ('0' + n).slice(-2);
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date
    .getHours()) + ':' + pad(date.getMinutes());
}

function displayTime(value) {
  if (!value) return '';
  const date = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value);
  return Number.isNaN(date.getTime()) ? String(value).replace('T', ' ').slice(0, 16) : formatTime(date);
}

function origin(url) {
  const match = /^https?:\/\/[^/]+/i.exec(url);
  return match ? match[0].toLowerCase() : '';
}

function request(url, data = {}, method = 'GET', retried = false) {
  const own = origin(url) === origin(config.root());
  const header = {
    'Content-Type': 'application/json'
  };
  if (own && wx.getStorageSync('token')) header.Authorization = wx.getStorageSync('token');
  return new Promise((resolve, reject) => {
    wx.request({
      url,
      data,
      method,
      header,
      success(res) {
        const authError = own && (res.statusCode === 401 || [3002, 3003, 3004, 3005].indexOf(res
          .data && res.data.errno) >= 0);
        if (authError) {
          if (retried || /auth\/loginByWeixin/.test(url)) {
            wx.removeStorageSync('token');
            wx.removeStorageSync('userInfo');
            reject(new Error('登录已过期，请重新登录'));
            return;
          }
          if (!loginPending) loginPending = getUserInfo().then(backendLogin).then(() => {
            loginPending = null;
          }, error => {
            loginPending = null;
            throw error;
          });
          loginPending.then(() => request(url, data, method, true)).then(resolve).catch(error => {
            wx.removeStorageSync('token');
            const pages = getCurrentPages();
            if (!pages.length || pages[pages.length - 1].route !== 'pages/auth/auth') wx
              .navigateTo({
                url: '/pages/auth/auth'
              });
            reject(error);
          });
          return;
        }
        if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data);
        else reject(new Error((res.data && res.data.errmsg) || '请求失败，请稍后重试'));
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'));
      }
    });
  });
}

function api(path, data, method) {
  return request(config.root() + path.replace(/^\//, ''), data, method).then(res => {
    if (!res || res.errno !== 0) throw new Error((res && res.errmsg) || '操作失败');
    return res.data;
  });
}

function login() {
  return new Promise((resolve, reject) => wx.login({
    success: res => res.code ? resolve(res) : reject(new Error('微信登录失败')),
    fail: reject
  }));
}

function checkSession() {
  return new Promise(resolve => wx.checkSession({
    success: () => resolve(true),
    fail: () => resolve(false)
  }));
}

function getUserInfo() {
  return new Promise((resolve, reject) => wx.getUserInfo({
    withCredentials: true,
    success: resolve,
    fail: reject
  }));
}

function backendLogin(detail) {
  return login().then(res => api('/auth/loginByWeixin', {
    code: res.code,
    detail
  }, 'POST')).then(data => {
    wx.setStorageSync('userInfo', data.userInfo);
    wx.setStorageSync('token', data.token);
    const app = getApp();
    app.globalData.userInfo = data.userInfo;
    app.globalData.token = data.token;
    return data.userInfo;
  });
}

function showErrorToast(error) {
  wx.showToast({
    title: typeof error === 'string' ? error : error.message || error.errmsg || error.errMsg || '操作失败',
    icon: 'none'
  });
}

function requireLogin() {
  if (wx.getStorageSync('token') && wx.getStorageSync('userInfo')) return true;
  wx.navigateTo({
    url: '/pages/auth/auth'
  });
  return false;
}

function uploadImages(count) {
  return new Promise((resolve, reject) => wx.chooseImage({
      count: Math.min(count, 9),
      sizeType: ['compressed'],
      success: resolve,
      fail: reject
    }))
    .then(res => Promise.all(res.tempFilePaths.map(path => new Promise((resolve, reject) => {
      wx.uploadFile({
        url: config.root() + 'upload/image',
        filePath: path,
        name: 'file',
        header: {
          Authorization: wx.getStorageSync('token') || ''
        },
        success(response) {
          try {
            const data = JSON.parse(response.data);
            if (response.statusCode === 200 && data.errno === 0 && data.data) resolve(data.data);
            else reject(new Error(data.errmsg || '图片上传失败'));
          } catch (error) {
            reject(new Error('图片上传失败'));
          }
        },
        fail: reject
      });
    }))));
}

function confirm(content) {
  return new Promise(resolve => wx.showModal({
    title: '温馨提示',
    content,
    success: res => resolve(res.confirm),
    fail: () => resolve(false)
  }));
}

function id() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
}
module.exports = {
  formatTime,
  displayTime,
  request,
  api,
  login,
  checkSession,
  getUserInfo,
  backendLogin,
  showErrorToast,
  requireLogin,
  uploadImages,
  confirm,
  id
};
