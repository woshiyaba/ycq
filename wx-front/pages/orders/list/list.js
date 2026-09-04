const util = require('../../../utils/util.js');
const orderUtil = require('../../../utils/order.js');
Page({
  data: {
    role: 'buyer',
    status: '',
    reviewOnly: false,
    orders: [],
    page: 1,
    hasMore: true,
    loading: false,
    error: ''
  },
  onLoad(options) {
    this.setData({
      role: options.role || 'buyer',
      status: options.status || '',
      reviewOnly: options.review === '1'
    });
    wx.setNavigationBarTitle({
      title: options.review === '1' ? '待评价' : options.role === 'seller' ? '我卖出的' : '我买到的'
    });
  },
  onShow() {
    if (util.requireLogin()) this.reload();
  },
  async reload() {
    if (this.data.loading) {
      this._reload = true;
      return;
    }
    this.setData({
      orders: [],
      page: 1,
      hasMore: true
    });
    await this.load();
  },
  switchStatus(e) {
    if (this.data.loading) return;
    this.setData({
      status: e.currentTarget.dataset.status,
      reviewOnly: false
    });
    this.reload();
  },
  async load() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({
      loading: true,
      error: ''
    });
    try {
      const data = await util.api('/goods/orders', {
        role: this.data.role,
        status: this.data.status,
        page: this.data.page,
        size: 10
      });
      let items = (data.items || []).map(orderUtil.decorate);
      if (this.data.reviewOnly) items = items.filter(item => !item.review);
      this.setData({
        orders: this.data.orders.concat(items),
        page: this.data.page + 1,
        hasMore: data.hasMore
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
      if (this._reload) {
        this._reload = false;
        this.reload();
      }
    }
  },
  open(e) {
    wx.navigateTo({
      url: '/pages/orders/detail/detail?id=' + e.currentTarget.dataset.id
    });
  },
  onPullDownRefresh() {
    this.reload();
  },
  onReachBottom() {
    this.load();
  }
});
