<template>
  <view class="group-info">
    <NavBar title="群聊信息" class="page-nav" />
    <view class="group-info__card">
      <text class="group-info__title">群成员（{{ members.length }}）</text>
      <view class="member-item" v-for="m in members" :key="m.userId">
        <image class="member-item__avatar" :src="m.images || '/static/logo.png'" mode="aspectFill" @click="goMemberHome(m)" />
        <view class="member-item__body">
          <text class="member-item__name ellipsis-1">
            {{ m.groupNickname || m.nickName || `用户${m.userId}` }}
            <text class="member-item__me" v-if="m.userId === myId">（我）</text>
          </text>
          <text class="member-item__joined">{{ formatTime(m.joinTime) }} 加入</text>
        </view>
        <text class="member-item__role" :class="`is-role-${m.role}`">{{ roleText(m.role) }}</text>
        <text
          class="member-item__more"
          v-if="canManage(m)"
          @click.stop="manageMember(m)"
        >管理</text>
      </view>
    </view>

    <button class="brand-btn-plain group-info__leave" v-if="myRole !== 2" @click="leave">退出群聊</button>
    <button class="brand-btn group-info__dissolve" v-if="myRole === 2" @click="dissolve">解散群聊</button>
  </view>
</template>

<script>
import { groupMembers, removeMember, setAdmin, leaveGroup, dissolveGroup } from '@/api/chat.js'
import { getToken } from '@/utils/request.js'

/**
 * 群聊信息：成员列表（角色 0成员 1管理 2群主）+ 群主/管理操作
 * - 群主/管理：可"移除成员"；仅群主可"设为管理员"
 * - 群主：解散群聊；普通成员：退群（群主不可退，只能解散）
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      groupId: 0,
      members: [],
      myId: 0,
      myRole: 0
    }
  },
  onLoad(options) {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    this.groupId = Number(options.groupId)
    this.myId = (uni.getStorageSync('userInfo') || {}).id || 0
  },
  onShow() {
    this.loadMembers()
  },
  methods: {
    /** 点击成员头像 → 该用户的用户主页 */
    goMemberHome(m) {
      if (m.userId) {
        uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${m.userId}` })
      }
    },

    async loadMembers() {
      try {
        this.members = (await groupMembers(this.groupId)) || []
        const me = this.members.find((m) => m.userId === this.myId)
        this.myRole = me ? me.role : 0
      } catch (e) {
        // toast 已统一处理
      }
    },
    roleText(role) {
      return { 2: '群主', 1: '管理员', 0: '成员' }[role] || '成员'
    },
    /** 是否对该成员显示"管理"入口：我是群主/管理，目标是普通成员，且不是我自己 */
    canManage(m) {
      if (m.userId === this.myId) return false
      if (m.role !== 0) return false          // 群主与管理不可被管理
      return this.myRole === 2 || this.myRole === 1
    },
    manageMember(m) {
      const actions = this.myRole === 2 ? ['设为管理员', '移出群聊'] : ['移出群聊']
      uni.showActionSheet({
        itemList: actions,
        success: async (res) => {
          try {
            if (actions[res.tapIndex] === '设为管理员') {
              await setAdmin(this.groupId, m.userId)
              uni.showToast({ title: '已设为管理员', icon: 'none' })
            } else {
              await removeMember(this.groupId, m.userId)
              uni.showToast({ title: '已移出群聊', icon: 'none' })
            }
            this.loadMembers()
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    leave() {
      uni.showModal({
        title: '退出群聊',
        content: '确定退出该群聊吗？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await leaveGroup(this.groupId)
            uni.showToast({ title: '已退出', icon: 'none' })
            setTimeout(() => uni.navigateBack(), 500)
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    dissolve() {
      uni.showModal({
        title: '解散群聊',
        content: '解散后群聊与消息记录将不可用，确定吗？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await dissolveGroup(this.groupId)
            uni.showToast({ title: '群聊已解散', icon: 'none' })
            setTimeout(() => uni.navigateBack(), 500)
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    formatTime(t) {
      return String(t || '').replace('T', ' ').slice(0, 10)
    }
  }
}
</script>

<style lang="scss" scoped>
.group-info {
  min-height: 100vh;
  padding: 24rpx;
}

.group-info__card {
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 24rpx;
}

.group-info__title {
  display: block;
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1px solid $brand-line;

  &:last-child {
    border-bottom: none;
  }
}

.member-item__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.member-item__body {
  flex: 1;
  min-width: 0;
}

.member-item__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.member-item__me {
  color: $text-sub;
  font-weight: 400;
  font-size: 12px;
}

.member-item__joined {
  display: block;
  color: $text-sub;
  font-size: 11px;
  margin-top: 4rpx;
}

.member-item__role {
  flex-shrink: 0;
  font-size: 11px;
  padding: 4rpx 16rpx;
  border-radius: $radius-btn;

  &.is-role-2 {
    background: $brand-primary;
    color: #ffffff;
  }

  &.is-role-1 {
    background: rgba(107, 142, 90, 0.12);
    color: $brand-primary;
  }

  &.is-role-0 {
    background: $brand-bg-2;
    color: $text-sub;
  }
}

.member-item__more {
  flex-shrink: 0;
  color: $brand-primary;
  font-size: 13px;
  margin-left: 20rpx;
}

.group-info__leave,
.group-info__dissolve {
  margin-top: 40rpx;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
