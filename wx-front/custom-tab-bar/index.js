Component({
  data: {
    selected: 0,
    unread: 0,
    tabs: [{
        path: '/pages/index/index',
        name: '圈',
        icon: '/static/images/ic_menu_home_nor.png',
        selectedIcon: '/static/images/ic_menu_home_pressed.png'
      },
      {
        path: '/pages/category/index/index',
        name: '精选',
        icon: '/static/images/ic_menu_cate_nor.png',
        selectedIcon: '/static/images/ic_menu_cate_pressed.png'
      },
      {
        path: '/pages/post/nav2post/nav2post',
        name: '卖',
        sell: true
      },
      {
        path: '/pages/chat/chatIndex/chatIndex',
        name: '消息',
        icon: '/static/images/ic_menu_chat_nor.png',
        selectedIcon: '/static/images/ic_menu_chat_pressed.png'
      },
      {
        path: '/pages/ucenter/index/index',
        name: '我的',
        icon: '/static/images/ic_menu_me_nor.png',
        selectedIcon: '/static/images/ic_menu_me_pressed.png'
      }
    ]
  },
  lifetimes: {
    attached() {
      this.sync();
    }
  },
  pageLifetimes: {
    show() {
      this.sync();
    }
  },
  methods: {
    sync() {
      const pages = getCurrentPages();
      const current = pages.length ? '/' + pages[pages.length - 1].route : '';
      const index = this.data.tabs.findIndex(tab => tab.path === current);
      this.setData({
        selected: index < 0 ? 0 : index,
        unread: getApp().globalData.unreadCount || 0
      });
    },
    change(e) {
      const index = Number(e.currentTarget.dataset.index);
      wx.switchTab({
        url: this.data.tabs[index].path,
        success: () => this.setData({
          selected: index
        })
      });
    }
  }
});
