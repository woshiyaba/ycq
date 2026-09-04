const util = require('../../../utils/util.js');
const empty = {
  name: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: false
};
Page({
  data: {
    addresses: [],
    select: false,
    editing: false,
    form: empty,
    busy: false,
    error: ''
  },
  onLoad(options) {
    this.setData({
      select: options.select === '1'
    });
  },
  onShow() {
    this.load();
  },
  async load() {
    if (!util.requireLogin()) return;
    try {
      const data = await util.api('/goodsUser/addresses');
      this.setData({
        addresses: Array.isArray(data) ? data : data.items || [],
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
  edit(e) {
    this.setData({
      form: Object.assign({}, e.currentTarget.dataset.address || empty),
      editing: true
    });
  },
  input(e) {
    this.setData({
      ['form.' + e.currentTarget.dataset.field]: e.detail.value
    });
  },
  default (e) {
    this.setData({
      'form.isDefault': e.detail.value
    });
  },
  close() {
    this.setData({
      editing: false
    });
  },
  noop() {},
  choose(e) {
    if (!this.data.select) return;
    wx.setStorageSync('selectedAddress', e.currentTarget.dataset.address);
    wx.navigateBack();
  },
  async save() {
    if (this.data.busy) return;
    const form = this.data.form;
    if (!form.name.trim() || !/^[+0-9() -]{6,24}$/.test(form.phone) || !form.region.trim() || !form
      .detail
      .trim()) {
      util.showErrorToast('请填写收件人、有效联系电话及完整地址');
      return;
    }
    this.setData({
      busy: true
    });
    try {
      await util.api('/goodsUser/addresses' + (form.id ? '/' + form.id : ''), form, form.id ? 'PUT' :
        'POST');
      this.setData({
        editing: false
      });
      await this.load();
      wx.showToast({
        title: '保存成功'
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  async remove(e) {
    if (!await util.confirm('确定删除该地址？')) return;
    try {
      await util.api('/goodsUser/addresses/' + e.currentTarget.dataset.id, {}, 'DELETE');
      await this.load();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  onPullDownRefresh() {
    this.load();
  }
});
