const util = require('../../../utils/util.js');
const websocket = require('../../../services/websocket.js');
Page({
  data: {
    id: 0,
    history: [],
    otherSide: {},
    goods: null,
    content: null,
    isU1: false,
    me: '',
    avatar: '',
    offsetTime: '',
    hasMore: true,
    loading: false,
    sending: false,
    input: '',
    error: '',
    scrollTo: 'message-bottom'
  },
  onLoad(options) {
    const me = wx.getStorageSync('userInfo') || {};
    this.setData({
      id: options.id,
      me: me.openId || '',
      avatar: me.avatarUrl || '',
      offsetTime: new Date().toISOString()
    });
  },
  onShow() {
    if (!util.requireLogin()) return;
    this.reload();
    websocket.wsConnect().catch(error => this.setData({
      error: error.message
    }));
    if (this.unsubscribe) this.unsubscribe();
    this.unsubscribe = websocket.subscribe(message => {
      if (String(message.chatId) !== String(this.data.id) || ![1, 3].includes(message.messageType) ||
        String(message.senderId) === String(this.data.me)) return;
      this.setData({
        history: this.data.history.concat([{
          messageBody: message.messageBody,
          sendTime: util.displayTime(message.sendTime),
          mine: false
        }]),
        scrollTo: ''
      });
      this.setData({
        scrollTo: 'message-bottom'
      });
      websocket.lessBadge(1);
      util.api('/chat/flushUnread/' + this.data.id, {}, 'POST').catch(() => {});
    });
  },
  onHide() {
    if (this.unsubscribe) this.unsubscribe();
    this.unsubscribe = null;
  },
  onUnload() {
    if (this.unsubscribe) this.unsubscribe();
  },
  async reload() {
    this.setData({
      history: [],
      offsetTime: new Date().toISOString(),
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
      const data = await util.api('/chat/form/' + this.data.id, {
        offsetTime: this.data.offsetTime,
        size: 20
      });
      const history = (data.historyList || []).filter(item => item.messageType !== 2).map(item => Object
        .assign({}, item, {
          mine: !!data.isU1 === !!item.u1ToU2,
          sendTime: util.displayTime(item.sendTime)
        }));
      this.setData({
        history: history.concat(this.data.history),
        otherSide: data.otherSide || {},
        goods: data.goods || null,
        content: data.content || null,
        isU1: data.isU1,
        offsetTime: data.offsetTime,
        hasMore: (data.historyList || []).length >= 20
      });
      wx.setNavigationBarTitle({
        title: data.otherSide ? data.otherSide.nickName : '聊一聊'
      });
      await util.api('/chat/flushUnread/' + this.data.id, {}, 'POST');
    } catch (error) {
      this.setData({
        error: error.message
      });
    } finally {
      this.setData({
        loading: false
      });
      wx.stopPullDownRefresh();
    }
  },
  input(e) {
    this.setData({
      input: e.detail.value
    });
  },
  async send() {
    const draft = this.data.input;
    const body = draft.trim();
    if (!body || this.data.sending) return;
    if (!this.data.otherSide.openId || body.length > 2000) {
      this.setData({
        error: !this.data.otherSide.openId ? '聊天信息尚未加载完成' : '消息不能超过2000字'
      });
      return;
    }
    this.setData({
      sending: true,
      error: ''
    });
    try {
      const acknowledgement = await websocket.sendMessage(JSON.stringify({
        chatId: Number(this.data.id),
        receiverId: this.data.otherSide.openId,
        senderId: this.data.me,
        goodsId: this.data.goods ? this.data.goods.id : null,
        postId: this.data.content ? this.data.content.id : null,
        messageType: this.data.history.length ? 1 : 3,
        messageBody: body
      }));
      this.setData({
        input: this.data.input === draft ? '' : this.data.input,
        history: this.data.history.concat([{
          mine: true,
          messageBody: body,
          sendTime: util.displayTime(acknowledgement.sendTime)
        }]),
        scrollTo: ''
      });
      this.setData({
        scrollTo: 'message-bottom'
      });
    } catch (error) {
      this.setData({
        error: error.message === '发送状态未确认，请刷新会话后重试' ?
          '发送状态未确认，内容已保留，请刷新会话后重试' :
          (error.message || '发送失败') + '，内容已保留'
      });
    } finally {
      this.setData({
        sending: false
      });
    }
  },
  card() {
    if (this.data.content) wx.navigateTo({
      url: '/pages/content/detail/detail?id=' + this.data.content.id
    });
    else if (this.data.goods) wx.navigateTo({
      url: '/pages/goods/goods?id=' + this.data.goods.id
    });
  },
  buy() {
    wx.navigateTo({
      url: '/pages/orders/confirm/confirm?goodsId=' + this.data.goods.id
    });
  },
  onPullDownRefresh() {
    this.reload();
  }
});
