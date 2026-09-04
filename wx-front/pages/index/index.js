const util = require('../../utils/util.js');
const content = require('../../utils/content.js');
Page({
  data: {
    tab: 'local',
    keyword: '',
    shortcuts: [{
        name: '手机数码',
        icon: 'digital',
        keyword: '手机',
        match: ['手机', '数码']
      },
      {
        name: '人才招聘',
        icon: 'jobs',
        url: '/pages/recruit/index/index'
      },
      {
        name: '租房买房',
        icon: 'home',
        keyword: '房',
        match: ['房产', '租房']
      },
      {
        name: '萌宠之家',
        icon: 'pet',
        keyword: '宠物',
        match: ['宠物', '萌宠']
      },
      {
        name: '家电市场',
        icon: 'appliance',
        keyword: '家电',
        match: ['家电']
      },
      {
        name: '运城爆料',
        icon: 'news',
        url: '/pages/content/list/list?mode=community'
      },
      {
        name: '家政保洁',
        icon: 'cleaning',
        keyword: '家政',
        kind: 'RECRUITMENT'
      },
      {
        name: '家装市场',
        icon: 'decoration',
        keyword: '家装',
        match: ['家具', '家装']
      },
      {
        name: '爱车之家',
        icon: 'car',
        keyword: '汽车',
        match: ['汽车', '车辆']
      },
      {
        name: '供应链',
        icon: 'supply',
        keyword: '供应',
        match: ['供应']
      }
    ],
    banner: [],
    goods: [],
    entries: [],
    page: 1,
    hasMore: true,
    loading: false,
    error: ''
  },
  onLoad() {
    this.reload();
    this.loadCategories();
  },
  onShow() {
    if (this._shown && this.data.tab !== 'local') this.reload();
    this._shown = true;
  },
  async reload() {
    if (this.data.loading) {
      this._reload = true;
      return;
    }
    this.setData({
      page: 1,
      goods: [],
      entries: [],
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
      if (this.data.tab === 'local') {
        if (this.data.page === 1) {
          const data = await util.api('/index/index');
          this.setData({
            goods: data.indexGoodsList || [],
            banner: data.banner || [],
            page: 2
          });
        } else {
          const data = await util.api('/index/more', {
            page: this.data.page,
            size: 10
          });
          this.setData({
            goods: this.data.goods.concat(data || []),
            page: this.data.page + 1,
            hasMore: (data || []).length === 10
          });
        }
      } else {
        const data = await util.api('/post/entries', {
          kind: 'COMMUNITY',
          following: this.data.tab === 'following',
          page: this.data.page,
          size: 10
        });
        this.setData({
          entries: this.data.entries.concat(content.items(data)),
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
      wx.stopPullDownRefresh();
      if (this._reload) {
        this._reload = false;
        this.reload();
      }
    }
  },
  switchTab(e) {
    if (this.data.loading) return;
    const tab = e.currentTarget.dataset.tab;
    if (tab === 'following' && !util.requireLogin()) return;
    if (tab === this.data.tab) return;
    this.setData({
      tab
    });
    this.reload();
  },
  async loadCategories() {
    try {
      const data = await util.api('/catalog/index');
      const categories = (data.subCategory || []).concat(data.allCategory || []);
      const shortcuts = this.data.shortcuts.map(item => {
        if (item.url || !item.match) return item;
        const category = categories.find(category => item.match.some(word => (category.name || '')
          .indexOf(word) >= 0));
        return category ? Object.assign({}, item, {
          url: '/pages/category/list/list?id=' + category.id
        }) : item;
      });
      this.setData({
        shortcuts
      });
    } catch (error) {
      /* Categories remain searchable when the catalog is unavailable. */ }
  },
  inputSearch(e) {
    this.setData({
      keyword: e.detail.value
    });
  },
  search() {
    wx.navigateTo({
      url: '/pages/search/search?keyword=' + encodeURIComponent(this.data.keyword.trim())
    });
  },
  shortcut(e) {
    const item = e.currentTarget.dataset.item;
    wx.navigateTo({
      url: item.url || '/pages/search/search?kind=' + (item.kind || 'GOODS') + '&keyword=' +
        encodeURIComponent(item.keyword)
    });
  },
  go(e) {
    const url = e.currentTarget.dataset.url;
    if (e.currentTarget.dataset.tab) wx.switchTab({
      url
    });
    else wx.navigateTo({
      url
    });
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
  onPullDownRefresh() {
    this.reload();
  },
  onReachBottom() {
    this.load();
  },
  onShareAppMessage() {
    return {
      title: '运城圈 · 发现身边的好生活',
      path: '/pages/index/index'
    };
  }
});
