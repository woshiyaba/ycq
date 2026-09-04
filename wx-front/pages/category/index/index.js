const util = require('../../../utils/util.js');
Page({
  data: {
    categories: [],
    subcategories: [],
    current: 0,
    loading: false,
    error: ''
  },
  onLoad() {
    this.load();
  },
  async load() {
    this.setData({
      loading: true,
      error: ''
    });
    try {
      const data = await util.api('/catalog/index');
      this.setData({
        categories: data.allCategory || [],
        subcategories: data.subCategory || [],
        current: data.allCategory && data.allCategory.length ? data.allCategory[0].id : 0
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      this.setData({
        loading: false
      });
      wx.stopPullDownRefresh();
    }
  },
  async choose(e) {
    const current = e.currentTarget.dataset.id;
    this.setData({
      current
    });
    try {
      const data = await util.api('/catalog/' + current);
      this.setData({
        subcategories: data || []
      });
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  go(e) {
    wx.navigateTo({
      url: e.currentTarget.dataset.url
    });
  },
  onPullDownRefresh() {
    this.load();
  }
});
