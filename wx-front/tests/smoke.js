// Run: node wx-front/tests/smoke.js
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '..');
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
for (const route of app.pages) {
  const file = path.join(root, route);
  for (const ext of ['.js', '.json', '.wxml', '.wxss']) assert(fs.existsSync(file + ext), route + ext +
    ' missing');
  JSON.parse(fs.readFileSync(file + '.json', 'utf8'));
  const js = fs.readFileSync(file + '.js', 'utf8');
  new vm.Script(js, {
    filename: route
  });
  let page;
  vm.runInNewContext(js, {
    require: () => ({}),
    Page: value => {
      page = value;
    },
    getApp: () => ({
      post: {}
    }),
    wx: {
      getStorageSync: () => ({}),
      canIUse: () => true
    },
    console
  });
  const wxml = fs.readFileSync(file + '.wxml', 'utf8');
  for (const match of wxml.matchAll(
      /(?:bind|catch)(?:tap|input|change|confirm|scrolltoupper|getuserinfo)="([A-Za-z0-9_]+)"/g)) assert
    .strictEqual(typeof page[match[1]], 'function', route + ' missing handler ' + match[1]);
  for (const match of (js + '\n' + wxml).matchAll(/\/pages\/[A-Za-z0-9_/-]+/g)) assert(app.pages.includes(
    match[0].slice(1)), route + ' links to unregistered ' + match[0]);
}
const customRoot = path.join(root, 'custom-tab-bar', 'index');
for (const ext of ['.js', '.json', '.wxml', '.wxss']) assert(fs.existsSync(customRoot + ext));
let component;
vm.runInNewContext(fs.readFileSync(customRoot + '.js', 'utf8'), {
  Component: value => {
    component = value;
  },
  getCurrentPages: () => [{
    route: 'pages/chat/chatIndex/chatIndex'
  }],
  getApp: () => ({
    globalData: {
      unreadCount: 3
    }
  }),
  wx: {
    switchTab: options => {
      assert.strictEqual(options.url, '/pages/post/nav2post/nav2post');
      options.success();
    }
  }
});
const tab = {
  data: JSON.parse(JSON.stringify(component.data)),
  setData(data) {
    Object.assign(this.data, data);
  }
};
component.methods.sync.call(tab);
assert.strictEqual(tab.data.selected, 3);
assert.strictEqual(tab.data.unread, 3);
component.methods.change.call(tab, {
  currentTarget: {
    dataset: {
      index: 2
    }
  }
});
assert.strictEqual(tab.data.selected, 2);
const storage = {
  apiRoot: 'http://localhost:8080/',
  token: 'old-token',
  userInfo: {
    openId: 'u1'
  }
};
let handler, navigated = 0;
global.wx = {
  getStorageSync: key => storage[key],
  setStorageSync: (key, value) => {
    storage[key] = value;
  },
  removeStorageSync: key => {
    delete storage[key];
  },
  request: options => handler(options),
  login: options => options.success({
    code: 'test-code'
  }),
  getUserInfo: options => options.success({
    userInfo: {
      nickName: 'user'
    }
  }),
  navigateTo: () => {
    navigated++;
  },
  setTabBarBadge: () => {},
  removeTabBarBadge: () => {}
};
const testApp = {
  globalData: {}
};
global.getApp = () => testApp;
global.getCurrentPages = () => [];
const util = require('../utils/util.js');
(async () => {
  handler = options => {
    assert.strictEqual(options.header.Authorization, undefined);
    options.success({
      statusCode: 200,
      data: {
        errno: 0,
        data: 'external'
      }
    });
  };
  assert.strictEqual((await util.request('https://example.org/suggestion')).data, 'external');
  let protectedCalls = 0;
  handler = options => {
    if (options.url.endsWith('auth/loginByWeixin')) options.success({
      statusCode: 200,
      data: {
        errno: 0,
        data: {
          token: 'new-token',
          userInfo: {
            openId: 'u1'
          }
        }
      }
    });
    else if (++protectedCalls === 1) options.success({
      statusCode: 200,
      data: {
        errno: 3002,
        errmsg: 'expired'
      }
    });
    else {
      assert.strictEqual(options.header.Authorization, 'new-token');
      options.success({
        statusCode: 200,
        data: {
          errno: 0,
          data: 'retried'
        }
      });
    }
  };
  assert.strictEqual(await util.api('/protected'), 'retried',
    'original promise must settle after refreshing login');
  assert.strictEqual(protectedCalls, 2);
  handler = options => options.success({
    statusCode: 500,
    data: {
      errmsg: 'server failed'
    }
  });
  await assert.rejects(() => util.api('/failure'), /server failed/);
  handler = options => options.success({
    statusCode: 200,
    data: {
      errno: 4001,
      errmsg: 'not allowed'
    }
  });
  await assert.rejects(() => util.api('/forbidden'), /not allowed/);
  handler = options => options.success({
    statusCode: 200,
    data: {
      errno: 3002
    }
  });
  await assert.rejects(() => util.request('http://localhost:8080/protected', {}, 'GET', true), /登录已过期/);
  let connections = 0,
    messageHandler, openHandler, closeHandler, sendFailure = false;
  const sentMessages = [];
  wx.connectSocket = () => {
    connections++;
    return {
      onOpen: fn => {
        openHandler = fn;
      },
      onClose: fn => {
        closeHandler = fn;
      },
      onError: () => {},
      onMessage: fn => {
        messageHandler = fn;
      },
      close: () => {},
      send: options => {
        sentMessages.push(JSON.parse(options.data));
        if (sendFailure) options.fail({
          errMsg: 'transport failed'
        });
        else if (options.success) options.success({});
      }
    };
  };
  const websocket = require('../services/websocket.js');
  await assert.rejects(() => websocket.wsConnect(), /请先登录/);
  assert.strictEqual(connections, 0, 'guests must not open a socket');
  storage.token = 'token';
  storage.userInfo = {
    openId: 'u1'
  };
  const first = websocket.wsConnect(),
    second = websocket.wsConnect();
  assert.strictEqual(connections, 1);
  openHandler();
  await Promise.all([first, second]);
  let messages = 0;
  const unsubscribe = websocket.subscribe(() => {
    messages++;
  });
  messageHandler({
    data: JSON.stringify({
      errno: 0,
      data: {
        messageType: 1
      }
    })
  });
  unsubscribe();
  messageHandler({
    data: JSON.stringify({
      errno: 0,
      data: {
        messageType: 1
      }
    })
  });
  assert.strictEqual(messages, 1, 'unsubscribed pages must not receive messages');

  const timers = new Map();
  const originalSetTimeout = global.setTimeout,
    originalClearTimeout = global.clearTimeout;
  let nextTimer = 0;
  global.setTimeout = (callback, duration) => {
    assert.strictEqual(duration, 15000);
    timers.set(++nextTimer, callback);
    return nextTimer;
  };
  global.clearTimeout = id => timers.delete(id);
  const emit = response => messageHandler({
    data: JSON.stringify(response)
  });
  const message = {
    chatId: 7,
    receiverId: 'u2',
    messageType: 1,
    messageBody: 'hello'
  };
  let acknowledgementEvents = 0;
  const stopAcknowledgements = websocket.subscribe(() => {
    acknowledgementEvents++;
  });
  try {
    let settled = false;
    const sending = websocket.sendMessage(JSON.stringify(message));
    sending.then(() => {
      settled = true;
    });
    for (let i = 0; i < 6; i++) await Promise.resolve();
    assert.strictEqual(settled, false, 'transport success must not be reported as a saved message');
    const firstId = sentMessages[sentMessages.length - 1].clientMessageId;
    assert(/^[A-Za-z0-9._:-]{1,80}$/.test(firstId));
    assert.strictEqual(timers.size, 1);
    const badgeBefore = testApp.globalData.unreadCount;
    emit({
      errno: 0,
      data: {
        messageType: 5,
        clientMessageId: firstId,
        chatId: 7,
        sendTime: '2026-09-04T08:00:00Z'
      }
    });
    assert.strictEqual((await sending).chatId, 7);
    assert.strictEqual(timers.size, 0, 'ACK must release its timer');
    assert.strictEqual(acknowledgementEvents, 0, 'ACK is not conversation content');
    assert.strictEqual(testApp.globalData.unreadCount, badgeBefore, 'ACK must not increase unread count');

    const denied = websocket.sendMessage(message);
    const denialCheck = assert.rejects(denied, /denied/);
    await Promise.resolve();
    const deniedId = sentMessages[sentMessages.length - 1].clientMessageId;
    assert.notStrictEqual(deniedId, firstId);
    emit({
      errno: 6001,
      errmsg: 'denied',
      data: {
        clientMessageId: deniedId
      }
    });
    await denialCheck;
    assert.strictEqual(timers.size, 0, 'server rejection must release its timer');

    sendFailure = true;
    await assert.rejects(websocket.sendMessage(message), /transport failed/);
    sendFailure = false;
    assert.strictEqual(timers.size, 0);

    const timeoutCheck = assert.rejects(websocket.sendMessage(message), /状态未确认/);
    await Promise.resolve();
    timers.values().next().value();
    await timeoutCheck;
    assert.strictEqual(timers.size, 0);

    const closedCheck = assert.rejects(websocket.sendMessage(message), /连接已关闭/);
    await Promise.resolve();
    closeHandler();
    await closedCheck;
    assert.strictEqual(timers.size, 0, 'disconnect must reject pending sends and release timers');

    const reconnect = websocket.wsConnect();
    openHandler();
    await reconnect;
    const hiddenCheck = assert.rejects(websocket.sendMessage(message), /连接已关闭/);
    await Promise.resolve();
    websocket.wsClose();
    await hiddenCheck;
    assert.strictEqual(timers.size, 0, 'closing the app must not leave ACK timers behind');
  } finally {
    stopAcknowledgements();
    global.setTimeout = originalSetTimeout;
    global.clearTimeout = originalClearTimeout;
  }

  let chatPage, sendCalls = 0,
    sendResult;
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/chat/chatForm/chatForm.js'), 'utf8'), {
    Page: value => {
      chatPage = value;
    },
    require: module => module.includes('websocket') ? {
      sendMessage: () => {
        sendCalls++;
        return sendResult;
      }
    } : {
      displayTime: value => value,
      api: async route => route.includes('/chat/form/') ? {
        historyList: Array.from({
          length: 21
        }, () => ({
          messageType: 1,
          u1ToU2: true
        })),
        isU1: true,
        otherSide: {
          openId: 'u2'
        }
      } : null
    },
    wx: {
      setNavigationBarTitle: () => {},
      stopPullDownRefresh: () => {}
    }
  });
  const chat = {
    data: JSON.parse(JSON.stringify(chatPage.data)),
    setData(value) {
      Object.assign(this.data, value);
    }
  };
  chat.data.input = 'draft';
  await chatPage.send.call(chat);
  assert.strictEqual(sendCalls, 0, 'sending is disabled before the recipient has loaded');
  chat.data.otherSide = {
    openId: 'u2'
  };
  chat.data.input = 'x'.repeat(2001);
  await chatPage.send.call(chat);
  assert.strictEqual(sendCalls, 0);
  chat.data.input = 'draft';
  sendResult = Promise.reject(new Error('server denied'));
  await chatPage.send.call(chat);
  assert.strictEqual(chat.data.input, 'draft');
  assert.strictEqual(chat.data.history.length, 0);
  assert.strictEqual(chat.data.error, 'server denied，内容已保留');
  sendResult = Promise.reject(new Error('发送状态未确认，请刷新会话后重试'));
  await chatPage.send.call(chat);
  assert.strictEqual(chat.data.input, 'draft');
  assert.strictEqual(chat.data.error, '发送状态未确认，内容已保留，请刷新会话后重试');
  sendResult = Promise.resolve({
    sendTime: 'server-time'
  });
  await chatPage.send.call(chat);
  assert.strictEqual(chat.data.input, '');
  assert.strictEqual(chat.data.history[0].sendTime, 'server-time');
  let acknowledge;
  sendResult = new Promise(resolve => {
    acknowledge = resolve;
  });
  chat.data.input = 'second message';
  const pendingPageSend = chatPage.send.call(chat);
  assert.strictEqual(chat.data.history.length, 1, 'outgoing bubbles wait for persistence ACK');
  chat.data.input = 'next draft';
  acknowledge({
    sendTime: 'second-server-time'
  });
  await pendingPageSend;
  assert.strictEqual(chat.data.input, 'next draft', 'ACK must not erase text edited while awaiting it');
  await chatPage.load.call(chat);
  assert.strictEqual(chat.data.hasMore, true, 'an expanded same-second page can exceed 20 messages');
  websocket.wsClose();
  console.log('OK: ' + app.pages.length +
    ' registered pages, navigation and event bindings; request/auth and WebSocket regression checks.');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
