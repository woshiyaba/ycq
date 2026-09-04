const util = require('../../../utils/util.js');
Page({
  data: {
    id: '',
    form: {
      name: '',
      desc: '',
      regionId: 0,
      region: '',
      categoryId: 0,
      price: '',
      marketPrice: '',
      postage: '0',
      ableSelfTake: true,
      ableMeet: false,
      ableExpress: false,
      images: []
    },
    categoryName: '',
    busy: false,
    uploading: false,
    error: ''
  },
  async onLoad(options) {
    if (!util.requireLogin()) return;
    if (options.id) {
      this.setData({
        id: options.id
      });
      wx.setNavigationBarTitle({
        title: '编辑好物'
      });
      try {
        const data = await util.api('/goods/detail/' + options.id);
        const info = data.info;
        this.setData({
          form: {
            name: info.name,
            desc: info.desc,
            regionId: info.regionId,
            region: info.region,
            categoryId: info.categoryId,
            price: info.price,
            marketPrice: info.marketPrice || '',
            postage: info.postage || '0',
            ableSelfTake: !!info.ableSelfTake,
            ableMeet: !!info.ableMeet,
            ableExpress: !!info.ableExpress,
            images: (data.gallery || []).map(item => item.imgUrl)
          },
          categoryName: info.categoryName || '已选择分类'
        });
      } catch (error) {
        this.setData({
          error: error.message
        });
      }
    }
  },
  onShow() {
    const post = getApp().post;
    if (post.region.id) {
      this.setData({
        'form.regionId': post.region.id,
        'form.region': post.region.name
      });
      post.region = {
        id: 0,
        name: ''
      };
    }
    if (post.cate.id) {
      this.setData({
        'form.categoryId': post.cate.id,
        categoryName: post.cate.name
      });
      post.cate = {
        id: 0,
        name: ''
      };
    }
  },
  input(e) {
    this.setData({
      ['form.' + e.currentTarget.dataset.field]: e.detail.value
    });
  },
  trade(e) {
    this.setData({
      'form.ableSelfTake': e.detail.value.indexOf('ableSelfTake') >= 0,
      'form.ableMeet': e.detail.value.indexOf('ableMeet') >= 0,
      'form.ableExpress': e.detail.value.indexOf('ableExpress') >= 0
    });
  },
  async addImages() {
    if (this.data.uploading) return;
    this.setData({
      uploading: true
    });
    try {
      const images = await util.uploadImages(9 - this.data.form.images.length);
      this.setData({
        'form.images': this.data.form.images.concat(images)
      });
    } catch (error) {
      if (!/cancel/.test(error.errMsg || '')) util.showErrorToast(error);
    } finally {
      this.setData({
        uploading: false
      });
    }
  },
  removeImage(e) {
    const images = this.data.form.images.slice();
    images.splice(e.currentTarget.dataset.index, 1);
    this.setData({
      'form.images': images
    });
  },
  async submit() {
    if (this.data.busy || this.data.uploading || !util.requireLogin()) return;
    const form = Object.assign({}, this.data.form);
    if (!form.name.trim() || !form.desc.trim() || !form.regionId || !form.categoryId || !form.images
      .length) {
      util.showErrorToast('请填写标题、描述、地区、分类并上传图片');
      return;
    }
    const amount = /^\d+(\.\d{1,2})?$/;
    form.marketPrice = form.marketPrice || '0';
    form.postage = form.ableExpress ? (form.postage || '0') : '0';
    if (!amount.test(String(form.price)) || Number(form.price) <= 0 || !amount.test(String(form
        .marketPrice)) || !amount.test(String(form.postage))) {
      util.showErrorToast('请填写有效金额，最多两位小数');
      return;
    }
    if (!form.ableSelfTake && !form.ableMeet && !form.ableExpress) {
      util.showErrorToast('请选择至少一种交易方式');
      return;
    }
    this.setData({
      busy: true
    });
    try {
      const data = await util.api(this.data.id ? '/goods/manage/' + this.data.id : '/post/post', form,
        this.data.id ? 'PUT' : 'POST');
      const id = this.data.id || (data && data.id);
      if (id) wx.redirectTo({
        url: '/pages/goods/goods?id=' + id
      });
      else wx.switchTab({
        url: '/pages/index/index'
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
