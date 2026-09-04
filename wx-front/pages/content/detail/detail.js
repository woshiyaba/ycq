const util = require('../../../utils/util.js');
const content = require('../../../utils/content.js');
Page({
  data: {
    id: '',
    entry: null,
    mine: false,
    comments: [],
    commentPage: 1,
    commentsMore: true,
    commentLoading: false,
    reply: null,
    commentBody: '',
    commentOpen: false,
    busy: false,
    error: '',
    me: ''
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
    try {
      const entry = content.decorate(await util.api('/post/entries/' + this.data.id));
      const me = (wx.getStorageSync('userInfo') || {}).openId;
      this.setData({
        entry,
        mine: String(entry.authorId) === String(me),
        me,
        error: '',
        comments: [],
        commentPage: 1,
        commentsMore: true
      });
      wx.setNavigationBarTitle({
        title: entry.kind === 'RECRUITMENT' ? '职位详情' : '圈友动态'
      });
      if (me) util.api('/goodsUser/history', {
        kind: entry.kind,
        targetId: entry.id
      }, 'POST').catch(() => {});
      await this.loadComments();
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      wx.stopPullDownRefresh();
    }
  },
  async loadComments() {
    if (this.data.commentLoading || !this.data.commentsMore) return;
    this.setData({
      commentLoading: true
    });
    try {
      const data = await util.api('/post/entries/' + this.data.id + '/comments', {
        page: this.data.commentPage,
        size: 20
      });
      this.setData({
        comments: this.data.comments.concat((data.items || []).map(item => Object.assign({}, item, {
          createdAt: util.displayTime(item.createdAt)
        }))),
        commentPage: this.data.commentPage + 1,
        commentsMore: data.hasMore
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        commentLoading: false
      });
    }
  },
  async toggle(e) {
    if (!util.requireLogin() || this.data.busy) return;
    const type = e.currentTarget.dataset.type;
    const field = type === 'like' ? 'liked' : 'favorited';
    this.setData({
      busy: true
    });
    try {
      await util.api('/post/entries/' + this.data.id + '/' + type, {}, this.data.entry[field] ?
        'DELETE' : 'PUT');
      const entry = Object.assign({}, this.data.entry);
      entry[field] = !entry[field];
      if (type === 'like') entry.likeCount = Math.max(0, (entry.likeCount || 0) + (entry.liked ? 1 : -
        1));
      this.setData({
        entry
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  openComment(e) {
    if (!util.requireLogin()) return;
    this.setData({
      reply: e.currentTarget.dataset.comment || null,
      commentBody: '',
      commentOpen: true
    });
  },
  closeComment() {
    this.setData({
      commentOpen: false
    });
  },
  noop() {},
  inputComment(e) {
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
      await util.api('/post/entries/' + this.data.id + '/comments', {
        body: this.data.commentBody.trim(),
        replyCommentId: this.data.reply ? this.data.reply.id : null
      }, 'POST');
      this.setData({
        commentOpen: false,
        commentBody: ''
      });
      await this.load();
      wx.showToast({
        title: '留言成功'
      });
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
      await util.api('/post/comments/' + e.currentTarget.dataset.id, {}, 'DELETE');
      await this.load();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  async chat() {
    if (!util.requireLogin()) return;
    try {
      const data = await util.api('/post/entries/' + this.data.id + '/chat', {}, 'POST');
      wx.navigateTo({
        url: '/pages/chat/chatForm/chatForm?id=' + data.chatId
      });
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  call() {
    if (util.requireLogin()) wx.makePhoneCall({
      phoneNumber: this.data.entry.job.contactPhone
    });
  },
  author() {
    wx.navigateTo({
      url: '/pages/user/user?userId=' + this.data.entry.authorId
    });
  },
  edit() {
    wx.navigateTo({
      url: '/pages/content/form/form?id=' + this.data.id
    });
  },
  async status() {
    if (this.data.busy) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/post/entries/' + this.data.id + '/status', {
        status: this.data.entry.status === 'PUBLISHED' ? 'OFFLINE' : 'PUBLISHED'
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
    if (!await util.confirm('删除后将无法恢复，确定删除这条发布？')) return;
    try {
      await util.api('/post/entries/' + this.data.id, {}, 'DELETE');
      wx.navigateBack();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  preview(e) {
    wx.previewImage({
      urls: this.data.entry.images,
      current: e.currentTarget.dataset.url
    });
  },
  onPullDownRefresh() {
    this.load();
  },
  onReachBottom() {
    this.loadComments();
  },
  onShareAppMessage() {
    return {
      title: this.data.entry ? this.data.entry.title : '运城圈',
      path: '/pages/content/detail/detail?id=' + this.data.id
    };
  }
});
