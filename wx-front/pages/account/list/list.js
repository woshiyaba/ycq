const util = require('../../../utils/util.js');
const content = require('../../../utils/content.js');
Page({
  data: {
    mode: 'favorites',
    kind: 'GOODS',
    items: [],
    page: 1,
    loading: false,
    hasMore: true,
    error: ''
  },
  onLoad(options) {
    const mode = options.mode || 'favorites';
    this.setData({
      mode
    });
    wx.setNavigationBarTitle({
      title: {
        favorites: '我的收藏',
        goods: '我发布的好物',
        following: '我的关注',
        history: '浏览足迹'
      } [mode] || '我的记录'
    });
  },
  onShow() {
    if (util.requireLogin()) this.reload();
  },
  async reload() {
    if (this.data.loading) {
      this._reload = true;
      return;
    }
    this.setData({
      items: [],
      page: 1,
      hasMore: true
    });
    await this.load();
  },
  switchKind(e) {
    if (this.data.loading) return;
    this.setData({
      kind: e.currentTarget.dataset.kind
    });
    this.reload();
  },
  async load() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({
      loading: true,
      error: ''
    });
    try {
      const mode = this.data.mode;
      const query = {
        page: this.data.page,
        size: 10
      };
      let path;
      if (mode === 'favorites') path = this.data.kind === 'GOODS' ? '/goodsUser/collect' :
        '/post/favorites';
      else path = {
        goods: '/goods/manage',
        following: '/goodsUser/following',
        history: '/goodsUser/history'
      } [mode];
      if (mode === 'favorites' && this.data.kind !== 'GOODS') query.kind = this.data.kind;
      const data = await util.api(path, query);
      const list = Array.isArray(data) ? data : data.items || [];
      this.setData({
        items: this.data.items.concat(list.map(item => {
          item.historyKey = (item.kind || "GOODS") + "-" + item.id;
          return content.decorate(item);
        })),
        page: this.data.page + 1,
        hasMore: Array.isArray(data) ? list.length === 10 : data.hasMore
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
  open(e) {
    const item = e.currentTarget.dataset.item;
    const mode = this.data.mode;
    if (mode === 'following') {
      wx.navigateTo({
        url: '/pages/user/user?userId=' + item.openId
      });
      return;
    }
    const kind = item.kind || 'GOODS';
    wx.navigateTo({
      url: (kind === 'GOODS' ? '/pages/goods/goods?id=' : '/pages/content/detail/detail?id=') + item
        .id
    });
  },
  async clear() {
    if (!await util.confirm('确定清空全部浏览足迹？')) return;
    try {
      await util.api('/goodsUser/history/clear', {}, 'POST');
      await this.reload();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  async unfollow(e) {
    try {
      await util.api('/goodsUser/follow/' + e.currentTarget.dataset.id, {}, 'DELETE');
      await this.reload();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  onPullDownRefresh() {
    this.reload();
  },
  onReachBottom() {
    this.load();
  }
});
