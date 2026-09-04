const util = require('./util.js');
const tabRoutes = ['pages/index/index', 'pages/category/index/index', 'pages/post/nav2post/nav2post',
  'pages/chat/chatIndex/chatIndex', 'pages/ucenter/index/index'
];

const channels = [
  ['FOLLOWING', '关注'],
  ['RECOMMENDED', '推荐'],
  ['SQUARE', '广场'],
  ['NEW', '新品'],
  ['HOT', '热点'],
  ['CIRCLES', '圈子'],
  ['CLOTHING', '服饰'],
  ['RESOURCES', '找资源']
].map(item => ({
  key: item[0],
  name: item[1]
}));

const categories = {
  HOME: [
    ['mobile', '手机数码', 'digital'],
    ['jobs', '人才招聘', 'jobs'],
    ['housing', '租房买房', 'home'],
    ['pets', '萌宠之家', 'pet'],
    ['appliances', '家电市场', 'appliance'],
    ['news', '运城爆料', 'news'],
    ['cleaning', '家政保洁', 'cleaning'],
    ['home-improvement', '家装市场', 'decoration'],
    ['cars', '爱车之家', 'car'],
    ['supply', '供应链', 'supply']
  ],
  FEATURED: [
    ['mobile', '手机数码', 'digital'],
    ['office', '电脑办公', 'digital'],
    ['appliance-cleaning', '家电空洗', 'appliance'],
    ['housekeeping', '自营家政', 'cleaning'],
    ['renovation', '设计装修', 'decoration'],
    ['appliance-repair', '家电维修', 'appliance'],
    ['car-care', '汽车美容', 'car'],
    ['broadband', '宽带网络', 'supply'],
    ['food', '美食天地', 'home'],
    ['digital', '数码家电', 'digital']
  ]
};

function distribute(items, width) {
  const columns = [
    [],
    []
  ];
  const heights = [0, 0];
  items.forEach(source => {
    const priced = source.kind === 'GOODS' && source.price !== null && source.price !== undefined;
    const item = Object.assign({}, source, {
      feedKey: source.kind + '-' + source.id,
      priced,
      priceLabel: priced ? String(Number(source.price)) : '',
      author: source.author || {},
      title: source.title || '',
      description: source.description || ''
    });
    const column = heights[0] <= heights[1] ? 0 : 1;
    // Images and copy have fixed ratios/line caps, so columns stay stable while images load.
    const height = width * (priced ? 183 / 181 : 148.82 / 181) + (priced ? 80 : 66);
    columns[column].push(item);
    heights[column] += height + 8;
  });
  return columns;
}

function page(scene) {
  return {
    data: {
      scene,
      channels,
      channel: 'RECOMMENDED',
      categoryKey: '',
      categories: categories[scene].map(item => ({
        key: item[0],
        name: item[1],
        icon: item[2]
      })),
      keyword: '',
      appliedKeyword: '',
      chromeHeight: 44,
      columns: [
        [],
        []
      ],
      following: [],
      banners: [],
      page: 1,
      total: 0,
      hasMore: true,
      loading: false,
      error: ''
    },
    onLoad() {
      const system = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const capsule = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
      this._cardWidth = (system.windowWidth - 30) / 2;
      this._items = [];
      this.setData({
        chromeHeight: capsule && capsule.bottom ? capsule.bottom + 8 : (system.statusBarHeight || 20) + 44
      });
      this.reload();
    },
    onUnload() {
      this._version = (this._version || 0) + 1;
      this._destroyed = true;
    },
    onShow() {
      const viewer = (wx.getStorageSync('userInfo') || {}).openId || '';
      if (this._viewer !== undefined && this._viewer !== viewer) {
        if (!viewer && this.data.channel === 'FOLLOWING') this.setData({
          channel: 'RECOMMENDED'
        });
        this.reload();
      }
      this._viewer = viewer;
    },
    reload() {
      this._version = (this._version || 0) + 1;
      this._items = [];
      this.setData({
        columns: [
          [],
          []
        ],
        following: [],
        banners: [],
        page: 1,
        total: 0,
        hasMore: true,
        loading: false
      });
      return this.load();
    },
    async load() {
      if (this.data.loading || !this.data.hasMore || this._destroyed) return;
      const version = this._version;
      const currentPage = this.data.page;
      this.setData({
        loading: true,
        error: ''
      });
      try {
        const data = await util.api('/index/feed', {
          scene,
          channel: this.data.channel,
          categoryKey: this.data.categoryKey,
          keyword: this.data.appliedKeyword,
          page: currentPage,
          size: 20
        });
        if (version !== this._version || this._destroyed) return;
        const seen = new Set(this._items.map(item => item.kind + '-' + item.id));
        (data.items || []).forEach(item => {
          const key = item.kind + '-' + item.id;
          if (!seen.has(key)) {
            this._items.push(item);
            seen.add(key);
          }
        });
        this.setData({
          columns: distribute(this._items, this._cardWidth || 181.5),
          following: this._items.map(item => Object.assign({}, item, {
            feedKey: item.kind + '-' + item.id,
            author: item.author || {},
            dateLabel: util.displayTime(item.createdAt).slice(0, 10),
            priceLabel: item.price === null || item.price === undefined ? '' : String(Number(item
              .price)),
            images: item.images && item.images.length ? item.images : item.primaryPicUrl ? [item
              .primaryPicUrl
            ] : []
          })),
          banners: currentPage === 1 ? data.banners || [] : this.data.banners,
          page: currentPage + 1,
          total: data.total || 0,
          hasMore: !!data.hasMore
        });
      } catch (error) {
        if (version === this._version && !this._destroyed) this.setData({
          error: error.message || '加载失败，请重试'
        });
      } finally {
        if (version === this._version && !this._destroyed) {
          this.setData({
            loading: false
          });
          wx.stopPullDownRefresh();
        }
      }
    },
    switchChannel(e) {
      const channel = e.currentTarget.dataset.key;
      if (channel === 'FOLLOWING' && !util.requireLogin()) return;
      if (channel === this.data.channel && !this.data.categoryKey) return;
      this.setData({
        channel,
        categoryKey: ''
      });
      this.reload();
    },
    chooseCategory(e) {
      const key = e.currentTarget.dataset.key;
      if (key === 'jobs') {
        wx.navigateTo({
          url: '/pages/recruit/index/index'
        });
        return;
      }
      if (key === 'news') {
        this.setData({
          channel: 'SQUARE',
          categoryKey: ''
        });
      } else {
        this.setData({
          categoryKey: this.data.categoryKey === key ? '' : key,
          channel: 'RECOMMENDED'
        });
      }
      this.reload();
    },
    inputSearch(e) {
      this.setData({
        keyword: e.detail.value
      });
    },
    search() {
      this.setData({
        appliedKeyword: this.data.keyword.trim()
      });
      this.reload();
    },
    clearSearch() {
      this.setData({
        keyword: '',
        appliedKeyword: ''
      });
      this.reload();
    },
    openItem(e) {
      const item = e.currentTarget.dataset.item;
      wx.navigateTo({
        url: (item.kind === 'GOODS' ? '/pages/goods/goods?id=' : '/pages/content/detail/detail?id=') +
          item.id
      });
    },
    openAuthor(e) {
      const id = e.currentTarget.dataset.id;
      if (id) wx.navigateTo({
        url: '/pages/user/user?userId=' + encodeURIComponent(id)
      });
    },
    openBanner(e) {
      const link = e.currentTarget.dataset.link || '';
      const unavailable = () => wx.showToast({
        title: '该活动暂无可打开的页面',
        icon: 'none'
      });
      if (!/^\/?pages\/[A-Za-z0-9_/-]+(?:\?[^\s]*)?$/.test(link)) {
        unavailable();
        return;
      }
      const route = link.split('?')[0].replace(/^\//, '');
      // Native navigation accepts only pages registered in app.json.
      if (tabRoutes.indexOf(route) >= 0) wx.switchTab({
        url: '/' + route,
        fail: unavailable
      });
      else wx.navigateTo({
        url: '/' + link.replace(/^\//, ''),
        fail: unavailable
      });
    },
    onPullDownRefresh() {
      return this.reload();
    },
    onReachBottom() {
      return this.load();
    },
    onShareAppMessage() {
      return {
        title: scene === 'HOME' ? '运城圈 · 发现身边的好生活' : '运城圈 · 精选好物',
        path: scene === 'HOME' ? '/pages/index/index' : '/pages/category/index/index'
      };
    }
  };
}

module.exports = {
  page,
  distribute
};
