const util = require('../../utils/util.js');
Page({
  data: {
    id: 0,
    goods: null,
    gallery: [],
    seller: {},
    comments: [],
    related: [],
    mine: false,
    me: '',
    collected: false,
    commentOpen: false,
    commentBody: '',
    reply: null,
    busy: false,
    error: ''
  },
  onLoad(options) {
    this.setData({
      id: Number(options.id)
    });
  },
  onShow() {
    this.load();
  },
  async load() {
    try {
      const data = await util.api('/goods/detail/' + this.data.id);
      if (!data.info || data.info.isDelete) throw new Error('宝贝已删除');
      const me = (wx.getStorageSync('userInfo') || {}).openId;
      this.setData({
        goods: data.info,
        gallery: data.gallery || [],
        seller: data.seller || {},
        comments: (data.comment || []).map(item => {
          item.createTime = util.displayTime(item.createTime);
          (item.replyList || []).forEach(reply => {
            reply.createTime = util.displayTime(reply.createTime);
          });
          return item;
        }),
        mine: String(data.seller.openId) === String(me),
        me,
        collected: !!data.userHasCollect,
        error: ''
      });
      const related = await util.api('/goods/related/' + this.data.id, {
        page: 1,
        size: 6
      });
      this.setData({
        related: related || []
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      wx.stopPullDownRefresh();
    }
  },
  preview(e) {
    wx.previewImage({
      urls: this.data.gallery.map(item => item.imgUrl),
      current: e.currentTarget.dataset.url
    });
  },
  author() {
    wx.navigateTo({
      url: '/pages/user/user?userId=' + this.data.seller.openId
    });
  },
  async collect() {
    if (!util.requireLogin() || this.data.busy) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/goodsUser/collect/addordelete/' + this.data.id + '/' + this.data.collected, {},
        'POST');
      this.setData({
        collected: !this.data.collected
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  comment(e) {
    if (!util.requireLogin()) return;
    this.setData({
      commentOpen: true,
      reply: e.currentTarget.dataset.comment || null,
      commentBody: ''
    });
  },
  closeComment() {
    this.setData({
      commentOpen: false
    });
  },
  noop() {},
  input(e) {
    this.setData({
      commentBody: e.detail.value
    });
  },
  async sendComment() {
    if (this.data.busy || !this.data.commentBody.trim()) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/goods/comment/post/' + this.data.id, {
        content: this.data.commentBody.trim(),
        replyCommentId: this.data.reply ? this.data.reply.id : 0
      }, 'POST');
      this.setData({
        commentOpen: false
      });
      await this.load();
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  async removeComment(e) {
    if (!await util.confirm('确定删除这条留言？')) return;
    try {
      await util.api('/goods/comments/' + e.currentTarget.dataset.id, {}, 'DELETE');
      await this.load();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  async chat() {
    if (!util.requireLogin()) return;
    try {
      const data = await util.api('/goodsUser/want/' + this.data.id + '/' + this.data.seller.openId, {},
        'POST');
      wx.navigateTo({
        url: '/pages/chat/chatForm/chatForm?id=' + (data.chatId || data)
      });
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  buy() {
    if (util.requireLogin()) wx.navigateTo({
      url: '/pages/orders/confirm/confirm?goodsId=' + this.data.id
    });
  },
  edit() {
    wx.navigateTo({
      url: '/pages/post/post/post?id=' + this.data.id
    });
  },
  async status() {
    if (this.data.busy) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/goods/manage/' + this.data.id + '/status', {
        isSelling: !this.data.goods.isSelling
      }, 'PUT');
      await this.load();
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  async remove() {
    if (!await util.confirm('确定删除这件宝贝？')) return;
    try {
      await util.api('/post/delete/' + this.data.id, {}, 'DELETE');
      wx.navigateBack();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  openGoods(e) {
    wx.navigateTo({
      url: '/pages/goods/goods?id=' + e.currentTarget.dataset.id
    });
  },
  onPullDownRefresh() {
    this.load();
  },
  onShareAppMessage() {
    return {
      title: this.data.goods ? this.data.goods.name : '运城圈好物',
      path: '/pages/goods/goods?id=' + this.data.id
    };
  }
});
