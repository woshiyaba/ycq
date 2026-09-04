const util = require('../../../utils/util.js');
const websocket = require('../../../services/websocket.js');
Page({
  data: {
    tab: 'chat',
    chats: [],
    notifications: [],
    unreadCount: 0,
    maxId: 0,
    goodsMaxId: 0,
    offsetTime: '',
    page: 1,
    hasMore: true,
    loading: false,
    logged: false,
    error: ''
  },
  onShow() {
    const logged = !!wx.getStorageSync('token');
    this.setData({
      logged
    });
    if (logged) {
      this.reload();
      this.loadNotificationCount();
      websocket.wsConnect().catch(() => {});
      if (this.unsubscribe) this.unsubscribe();
      this.unsubscribe = websocket.subscribe(message => {
        if (message.messageType === 1 || message.messageType === 3) {
          if (this.data.tab === 'chat') this.reload();
        }
      });
    }
  },
  onHide() {
    if (this.unsubscribe) this.unsubscribe();
    this.unsubscribe = null;
  },
  onUnload() {
    if (this.unsubscribe) this.unsubscribe();
  },
  async loadNotificationCount() {
    try {
      const data = await util.api('/post/notifications', {
        page: 1,
        size: 1
      });
      this.setData({
        unreadCount: data.unreadCount || 0,
        maxId: data.maxId || 0,
        goodsMaxId: data.goodsMaxId || 0
      });
    } catch (error) {}
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
      chats: [],
      notifications: [],
      offsetTime: new Date().toISOString(),
      page: 1,
      hasMore: true
    });
    await this.load();
  },
  async load() {
    if (this.data.loading || !this.data.hasMore || !this.data.logged) return;
    this.setData({
      loading: true,
      error: ''
    });
    try {
      if (this.data.tab === 'chat') {
        const data = await util.api('/chat/index', {
          offsetTime: this.data.offsetTime,
          size: 10
        });
        this.setData({
          chats: this.data.chats.concat((data.chats || []).map(item => {
            item.lastChat.sendTime = util.displayTime(item.lastChat.sendTime);
            return item;
          })),
          offsetTime: data.offsetTime,
          hasMore: (data.chats || []).length === 10
        });
      } else {
        const data = await util.api('/post/notifications', {
          page: this.data.page,
          size: 20
        });
        this.setData({
          notifications: this.data.notifications.concat((data.items || []).map(item => Object
            .assign({}, item, {
              createdAt: util.displayTime(item.createdAt)
            }))),
          unreadCount: data.unreadCount || 0,
          maxId: data.maxId || 0,
          goodsMaxId: data.goodsMaxId || 0,
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
  openChat(e) {
    const chat = e.currentTarget.dataset.chat;
    websocket.lessBadge(chat.unreadCount || 0);
    wx.navigateTo({
      url: '/pages/chat/chatForm/chatForm?id=' + chat.lastChat.chatId
    });
  },
  async openNotification(e) {
    const item = e.currentTarget.dataset.item;
    try {
      const data = item.source === 'GOODS' ? {
        goodsIds: [item.id]
      } : {
        ids: [item.id]
      };
      await util.api('/post/notifications/read', data, 'POST');
    } catch (error) {
      util.showErrorToast(error);
      return;
    }
    wx.navigateTo({
      url: item.source === 'GOODS' ? '/pages/goods/goods?id=' + item.goodsId :
        '/pages/content/detail/detail?id=' + item.postId
    });
  },
  async readAll() {
    try {
      if (this.data.tab === 'chat') {
        await util.api('/chat/read-all', {}, 'POST');
        websocket.setBadge(0);
      } else {
        await util.api('/post/notifications/read-all', {
          maxId: this.data.maxId,
          goodsMaxId: this.data.goodsMaxId
        }, 'POST');
        this.setData({
          unreadCount: 0
        });
      }
      await this.reload();
    } catch (error) {
      util.showErrorToast(error);
    }
  },
  login() {
    util.requireLogin();
  },
  onPullDownRefresh() {
    if (this.data.logged) this.reload();
    else wx.stopPullDownRefresh();
  },
  onReachBottom() {
    this.load();
  }
});
