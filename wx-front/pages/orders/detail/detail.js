const util = require('../../../utils/util.js');
const orderUtil = require('../../../utils/order.js');
Page({
  data: {
    id: 0,
    order: null,
    isBuyer: false,
    isSeller: false,
    busy: false,
    error: '',
    trackingNo: '',
    reviewContent: '',
    rating: 5,
    ratings: [1, 2, 3, 4, 5],
    panel: ''
  },
  onLoad(options) {
    this.setData({
      id: options.id
    });
  },
  onShow() {
    this.load();
  },
  async load() {
    if (!util.requireLogin()) return;
    try {
      const order = orderUtil.decorate(await util.api('/goods/orders/' + this.data.id));
      const me = (wx.getStorageSync('userInfo') || {}).openId;
      this.setData({
        order,
        isBuyer: String(me) === String(order.buyerId),
        isSeller: String(me) === String(order.sellerId),
        error: ''
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      wx.stopPullDownRefresh();
    }
  },
  async action(e) {
    const action = e.currentTarget.dataset.action;
    if (this.data.busy) return;
    if (action === 'ship' || action === 'review') {
      this.setData({
        panel: action
      });
      return;
    }
    if (action === 'cancel' && !await util.confirm('确定取消这个订单？')) return;
    if (action === 'receive' && !await util.confirm('确认已经收到宝贝？确认后订单将完成。')) return;
    await this.perform(action, {});
  },
  async perform(action, data) {
    if (this.data.busy) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/goods/orders/' + this.data.id + '/' + action, data, 'POST');
      this.setData({
        panel: ''
      });
      await this.load();
      wx.showToast({
        title: action === 'pay' ? '模拟支付成功' : '操作成功'
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  input(e) {
    this.setData({
      [e.currentTarget.dataset.field]: e.detail.value
    });
  },
  rating(e) {
    this.setData({
      rating: Number(e.detail.value) + 1
    });
  },
  close() {
    this.setData({
      panel: ''
    });
  },
  noop() {},
  submitPanel() {
    if (this.data.panel === 'ship') {
      this.perform('ship', {
        trackingNo: this.data.trackingNo.trim()
      });
    } else {
      if (!this.data.reviewContent.trim()) {
        util.showErrorToast('请填写评价内容');
        return;
      }
      this.perform('review', {
        rating: this.data.rating,
        content: this.data.reviewContent.trim()
      });
    }
  },
  goods() {
    wx.navigateTo({
      url: '/pages/goods/goods?id=' + this.data.order.goodsId
    });
  },
  async chat() {
    try {
      const data = await util.api('/goodsUser/want/' + this.data.order.goodsId + '/' + this.data.order
        .sellerId, {}, 'POST');
      wx.navigateTo({
        url: '/pages/chat/chatForm/chatForm?id=' + (data.chatId || data)
      });
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  onPullDownRefresh() {
    this.load();
  }
});
