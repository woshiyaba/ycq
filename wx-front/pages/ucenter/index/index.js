const util = require('../../../utils/util.js');
Page({
  data: {
    profile: {},
    logged: false,
    error: ''
  },
  async onShow() {
    const logged = !!wx.getStorageSync('token');
    this.setData({
      logged,
      profile: wx.getStorageSync('userInfo') || {}
    });
    if (logged) {
      try {
        this.setData({
          profile: await util.api('/goodsUser/profile'),
          error: ''
        });
      } catch (error) {
        this.setData({
          error: error.message
        });
      }
    }
  },
  go(e) {
    if (!util.requireLogin()) return;
    wx.navigateTo({
      url: e.currentTarget.dataset.url
    });
  },
  profile() {
    if (util.requireLogin()) wx.navigateTo({
      url: '/pages/account/profile/profile'
    });
  },
  onPullDownRefresh() {
    this.onShow().then(() => wx.stopPullDownRefresh());
  }
});
