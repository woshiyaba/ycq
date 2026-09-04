const util = require('../../../utils/util.js');
const names = require('../../../utils/order.js').deliveryNames;
Page({
  data: {
    goodsId: 0,
    goods: null,
    methods: [],
    method: '',
    address: null,
    total: '0.00',
    postage: '0.00',
    busy: false,
    error: '',
    requestId: ''
  },
  onLoad(options) {
    this.setData({
      goodsId: Number(options.goodsId),
      requestId: util.id()
    });
    this.load();
  },
  onShow() {
    const selected = wx.getStorageSync('selectedAddress');
    if (selected) {
      this.setData({
        address: selected
      });
      wx.removeStorageSync('selectedAddress');
    }
  },
  async load() {
    if (!util.requireLogin()) return;
    try {
      const data = await util.api('/goods/detail/' + this.data.goodsId);
      const goods = data.info;
      const methods = [];
      if (goods.ableSelfTake) methods.push({
        id: 'SELF_TAKE',
        name: names.SELF_TAKE
      });
      if (goods.ableMeet) methods.push({
        id: 'MEET',
        name: names.MEET
      });
      if (goods.ableExpress) methods.push({
        id: 'EXPRESS',
        name: names.EXPRESS
      });
      if (!goods.isSelling) throw new Error('宝贝暂不可购买');
      if (!methods.length) throw new Error('卖家未设置交易方式');
      this.setData({
        goods,
        cover: data.gallery && data.gallery.length ? data.gallery[0].imgUrl : goods.primaryPicUrl,
        methods,
        method: methods[0].id,
        error: ''
      });
      this.calculate();
      if (!this.data.address) {
        const addresses = await util.api('/goodsUser/addresses');
        const items = Array.isArray(addresses) ? addresses : addresses.items || [];
        this.setData({
          address: items.find(item => item.isDefault) || items[0] || null
        });
      }
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      wx.stopPullDownRefresh();
    }
  },
  changeMethod(e) {
    this.setData({
      method: e.detail.value
    });
    this.calculate();
  },
  calculate() {
    const postage = this.data.method === 'EXPRESS' ? Number(this.data.goods.postage || 0) : 0;
    this.setData({
      postage: postage.toFixed(2),
      total: (Number(this.data.goods.price) + postage).toFixed(2)
    });
  },
  chooseAddress() {
    wx.navigateTo({
      url: '/pages/account/addresses/addresses?select=1'
    });
  },
  async submit() {
    if (this.data.busy || !this.data.goods) return;
    if (this.data.method === 'EXPRESS' && !this.data.address) {
      util.showErrorToast('请选择收货地址');
      return;
    }
    this.setData({
      busy: true
    });
    try {
      const order = await util.api('/goods/orders', {
        goodsId: this.data.goodsId,
        deliveryMethod: this.data.method,
        address: this.data.method === 'EXPRESS' ? this.data.address : null,
        requestId: this.data.requestId
      }, 'POST');
      wx.redirectTo({
        url: '/pages/orders/detail/detail?id=' + order.id
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  }
});
