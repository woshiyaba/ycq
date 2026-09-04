const util = require('../../../utils/util.js');
Page({
  go(e) {
    if (util.requireLogin()) wx.navigateTo({
      url: e.currentTarget.dataset.url
    });
  }
});
