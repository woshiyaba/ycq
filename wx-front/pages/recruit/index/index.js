const util = require('../../../utils/util.js');
const content = require('../../../utils/content.js');
Page({
  data: {
    keyword: '',
    tab: 'all',
    workType: '',
    entries: [],
    page: 1,
    hasMore: true,
    loading: false,
    error: ''
  },
  onLoad() {
    this.reload();
  },
  input(e) {
    this.setData({
      keyword: e.detail.value
    });
  },
  search() {
    this.reload();
  },
  switchTab(e) {
    if (this.data.loading) return;
    this.setData({
      tab: e.currentTarget.dataset.tab
    });
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
      const data = await util.api('/post/entries', {
        kind: 'RECRUITMENT',
        keyword: this.data.keyword,
        workType: this.data.tab === 'part' ? 'PART_TIME' : '',
        settlement: this.data.tab === 'day' ? 'WEEKLY,DAILY' : '',
        industry: this.data.tab === 'food' ? '餐饮服务' : '',
        sort: this.data.tab === 'part' ? 'salary' : 'latest',
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
      url: '/pages/content/form/form?kind=RECRUITMENT'
    });
  },
  onPullDownRefresh() {
    this.reload();
  },
  onReachBottom() {
    this.load();
  }
});
