const util = require('../../../utils/util.js');
Page({
  data: {
    id: 0,
    categories: [],
    goods: [],
    page: 1,
    loading: false,
    hasMore: true,
    error: ''
  },
  onLoad(options) {
    this.setData({
      id: Number(options.id)
    });
    this.initial();
  },
  async initial() {
    this.setData({
      loading: true,
      error: ''
    });
    try {
      const data = await util.api('/goods/category/index/' + this.data.id);
      this.setData({
        categories: data.brotherCategory || [],
        goods: data.goodsList || [],
        page: 2,
        hasMore: (data.goodsList || []).length >= 10
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
  async load() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({
      loading: true
    });
    try {
      const data = await util.api('/goods/category/' + this.data.id, {
        page: this.data.page,
        size: 10
      });
      this.setData({
        goods: this.data.goods.concat(data || []),
        page: this.data.page + 1,
        hasMore: (data || []).length === 10
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        loading: false
      });
    }
  },
  choose(e) {
    if (this.data.loading) return;
    this.setData({
      id: e.currentTarget.dataset.id,
      goods: [],
      page: 1,
      hasMore: true
    });
    this.load();
  },
  openGoods(e) {
    wx.navigateTo({
      url: '/pages/goods/goods?id=' + e.currentTarget.dataset.id
    });
  },
  onPullDownRefresh() {
    this.initial();
  },
  onReachBottom() {
    this.load();
  }
});
