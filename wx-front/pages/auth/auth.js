const util = require('../../utils/util.js');
const websocket = require('../../services/websocket.js');
Page({
  data: {
    busy: false,
    logged: false,
    error: ''
  },
  async startLogin(e) {
    if (this.data.busy) return;
    if (!e.detail || e.detail.errMsg && e.detail.errMsg.indexOf('fail') >= 0) {
      util.showErrorToast('需要微信授权后才能登录');
      return;
    }
    this.setData({
      busy: true,
      error: ''
    });
    try {
      await util.backendLogin(e.detail);
      this.setData({
        logged: true
      });
      websocket.wsConnect().catch(() => {});
      const pages = getCurrentPages();
      if (pages.length > 1) wx.navigateBack();
      else wx.switchTab({
        url: '/pages/ucenter/index/index'
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  back() {
    const pages = getCurrentPages();
    if (pages.length > 1) wx.navigateBack();
    else wx.switchTab({
      url: '/pages/index/index'
    });
  }
});
