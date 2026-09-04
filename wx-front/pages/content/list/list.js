const util = require('../../../utils/util.js');
const content = require('../../../utils/content.js');
Page({
  data: {
    mode: 'community',
    kind: 'COMMUNITY',
    entries: [],
    page: 1,
    hasMore: true,
    loading: false,
    error: ''
  },
  onLoad(options) {
    this.setData({
      mode: options.mode || 'community',
      kind: options.kind || 'COMMUNITY'
    });
    wx.setNavigationBarTitle({
      title: options.mode === 'mine' ? (options.kind === 'RECRUITMENT' ? '我的招聘' : '我的帖子') : options
        .mode === 'favorites' ? '我的收藏' : '同城生活'
    });
  },
  onShow() {
    if (this.data.mode !== 'community' && !util.requireLogin()) return;
    this.reload();
  },
  async reload() {
    if (this.data.loading) {
      this._reload = true;
      return;
    }
    this.setData({
      entries: [],
      page: 1,
      hasMore: true
    });
    await this.load();
  },
  async load() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({
      loading: true,
      error: ''
    });
    try {
      const path = this.data.mode === 'mine' ? '/post/mine' : this.data.mode === 'favorites' ?
        '/post/favorites' : '/post/entries';
      const data = await util.api(path, {
        kind: this.data.kind,
        page: this.data.page,
        size: 10
      });
      this.setData({
        entries: this.data.entries.concat(content.items(data)),
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
  openEntry(e) {
    wx.navigateTo({
      url: '/pages/content/detail/detail?id=' + e.currentTarget.dataset.id
    });
  },
  publish() {
    if (util.requireLogin()) wx.navigateTo({
      url: '/pages/content/form/form?kind=' + this.data.kind
    });
  },
  onPullDownRefresh() {
    this.reload();
  },
  onReachBottom() {
    this.load();
  }
});
