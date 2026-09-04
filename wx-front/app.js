const websocket = require('./services/websocket.js');
App({
  onLaunch() {
    this.globalData.userInfo = wx.getStorageSync('userInfo') || this.globalData.userInfo;
    this.globalData.token = wx.getStorageSync('token') || '';
  },
  onShow() {
    if (wx.getStorageSync('token')) websocket.wsConnect().catch(() => {});
  },
  onHide() {
    websocket.wsClose();
  },
  globalData: {
    unreadCount: 0,
    userInfo: {
      openId: '',
      nickName: 'Hi，欢迎来到运城圈',
      avatarUrl: ''
    },
    token: ''
  },
  post: {
    cate: {
      id: 0,
      name: ''
    },
    region: {
      id: 0,
      name: ''
    }
  }
});
