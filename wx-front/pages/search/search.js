const util = require('../../utils/util.js');
const content = require('../../utils/content.js');
Page({
  data: {
    keyword: '',
    kind: 'GOODS',
    history: [],
    hot: [],
    items: [],
    page: 1,
    loading: false,
    hasMore: true,
    searched: false,
    error: ''
  },
  onLoad(options) {
    this.keywords();
    const kind = ['GOODS', 'COMMUNITY', 'RECRUITMENT'].includes(options.kind) ? options.kind : 'GOODS';
    let keyword = options.keyword || '';
    try {
      keyword = decodeURIComponent(keyword);
    } catch (error) {}
    this.setData({
      kind,
      keyword
    });
    if (this.data.keyword.trim()) this.search();
  },
  async keywords() {
    try {
      const data = await util.api('/search/index');
      this.setData({
        history: data.historyKeywordList || [],
        hot: data.hotKeywordList || []
      });
    } catch (error) {}
  },
  input(e) {
    this.setData({
      keyword: e.detail.value
    });
  },
  choose(e) {
    this.setData({
      keyword: e.currentTarget.dataset.keyword
    });
    this.search();
  },
  switchKind(e) {
    if (this.data.loading) return;
    this.setData({
      kind: e.currentTarget.dataset.kind
    });
    if (this.data.keyword.trim()) this.search();
  },
  async search() {
    if (this.data.loading) return;
    if (!this.data.keyword.trim()) return;
    this.setData({
      items: [],
      page: 1,
      hasMore: true,
      searched: true
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
      if (this.data.kind === 'GOODS') {
        const items = await util.api('/search/result/' + encodeURIComponent(this.data.keyword.trim()), {
          page: this.data.page,
          size: 10
        });
        this.setData({
          items: this.data.items.concat(items || []),
          page: this.data.page + 1,
          hasMore: (items || []).length === 10
        });
        this.keywords();
      } else {
        const data = await util.api('/post/entries', {
          kind: this.data.kind,
          keyword: this.data.keyword.trim(),
          page: this.data.page,
          size: 10
        });
        this.setData({
          items: this.data.items.concat(content.items(data)),
          page: this.data.page + 1,
          hasMore: data.hasMore
        });
      }
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      this.setData({
        loading: false
      });
    }
  },
  async clear() {
    try {
      await util.api('/search/clearhistory');
      this.setData({
        history: []
      });
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  openGoods(e) {
    wx.navigateTo({
      url: '/pages/goods/goods?id=' + e.currentTarget.dataset.id
    });
  },
  openEntry(e) {
    wx.navigateTo({
      url: '/pages/content/detail/detail?id=' + e.currentTarget.dataset.id
    });
  },
  onReachBottom() {
    if (this.data.searched) this.load();
  }
});
