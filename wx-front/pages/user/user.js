const util = require('../../utils/util.js');
const content = require('../../utils/content.js');
Page({
  data: {
    userId: '',
    profile: {},
    mine: false,
    kind: 'GOODS',
    items: [],
    page: 1,
    hasMore: true,
    loading: false,
    busy: false,
    error: ''
  },
  onLoad(options) {
    this.setData({
      userId: options.userId,
      mine: String(options.userId) === String((wx.getStorageSync('userInfo') || {}).openId)
    });
    this.loadProfile();
    this.reload();
  },
  async loadProfile() {
    try {
      this.setData({
        profile: await util.api('/goodsUser/profile/' + encodeURIComponent(this.data.userId))
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
    }
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
  async load() {
    if (this.data.loading || !this.data.hasMore) return;
    this.setData({
      loading: true
    });
    try {
      if (this.data.kind === 'GOODS') {
        const data = await util.api('/goodsUser/user/more/' + encodeURIComponent(this.data.userId), {
          page: this.data.page,
          size: 10
        });
        let items = [];
        Object.keys(data || {}).forEach(key => {
          items = items.concat(data[key] || []);
        });
        const known = {};
        this.data.items.forEach(item => {
          known[item.id] = true;
        });
        const unique = items.filter(item => {
          if (known[item.id]) return false;
          known[item.id] = true;
          return true;
        });
        this.setData({
          items: this.data.items.concat(unique),
          page: this.data.page + 1,
          hasMore: items.length >= 10
        });
      } else {
        const data = await util.api('/post/entries', {
          kind: this.data.kind,
          authorId: this.data.userId,
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
      wx.stopPullDownRefresh();
      if (this._reload) {
        this._reload = false;
        this.reload();
      }
    }
  },
  switchKind(e) {
    if (this.data.loading) return;
    this.setData({
      kind: e.currentTarget.dataset.kind
    });
    this.reload();
  },
  async follow() {
    if (!util.requireLogin() || this.data.busy) return;
    this.setData({
      busy: true
    });
    try {
      await util.api('/goodsUser/follow/' + encodeURIComponent(this.data.userId), {}, this.data.profile
        .following ? 'DELETE' : 'PUT');
      await this.loadProfile();
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  edit() {
    wx.navigateTo({
      url: '/pages/account/profile/profile'
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
  onReachBottom() {
    this.load();
  },
  onPullDownRefresh() {
    this.loadProfile();
    this.reload();
  }
});
