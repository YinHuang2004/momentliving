<template>
  <view class="group-create">
    <NavBar title="发起群聊" class="page-nav" />
    <view class="group-create__field">
      <text class="group-create__label">群名称</text>
      <input
        class="group-create__input"
        v-model="groupName"
        maxlength="20"
        placeholder="给群起个名字（最多 20 字）"
        placeholder-class="group-create__placeholder"
      />
    </view>

    <view class="group-create__field">
      <text class="group-create__label">选择成员</text>
      <view class="group-create__chips" v-if="selected.length > 0">
        <view class="group-create__chip" v-for="u in selected" :key="u.id" @click="removeMember(u)">
          <text class="group-create__chip-text">{{ u.nickName || `用户${u.id}` }} ×</text>
        </view>
      </view>
      <view class="group-create__search">
        <input
          class="group-create__input"
          v-model="keyword"
          placeholder="搜索昵称或手机号添加成员"
          placeholder-class="group-create__placeholder"
          confirm-type="search"
          @confirm="doSearch"
        />
        <view class="group-create__btn" @click="doSearch">
          <text class="group-create__btn-text">搜索</text>
        </view>
      </view>
      <view class="user-item" v-for="u in results" :key="u.id" @click="addMember(u)">
        <image class="user-item__avatar" :src="u.images || '/static/logo.png'" mode="aspectFill" />
        <view class="user-item__body">
          <text class="user-item__name ellipsis-1">{{ u.nickName || `用户${u.id}` }}</text>
        </view>
        <text class="user-item__action" :class="{ 'is-added': isSelected(u) }">
          {{ isSelected(u) ? '已选' : '添加' }}
        </text>
      </view>
      <view class="group-create__empty" v-if="searched && results.length === 0">
        <text>没有找到相关用户</text>
      </view>
    </view>

    <button class="brand-btn group-create__submit" :class="{ 'is-disabled': !canSubmit }" @click="submit">
      创建群聊{{ selected.length > 0 ? `（${selected.length + 1} 人）` : '' }}
    </button>
  </view>
</template>

<script>
import { searchUsers, createGroup } from '@/api/chat.js'
import { getToken } from '@/utils/request.js'

/**
 * 建群：群名 + 搜索选成员 → 创建后进群聊（创建者为群主，角色 2）
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      groupName: '',
      keyword: '',
      results: [],
      selected: [],
      loading: false,
      searched: false,
      submitting: false
    }
  },
  computed: {
    canSubmit() {
      return this.groupName.trim() && this.selected.length > 0 && !this.submitting
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
    }
  },
  methods: {
    async doSearch() {
      const kw = (this.keyword || '').trim()
      if (!kw) return
      this.loading = true
      try {
        this.results = (await searchUsers(kw)) || []
        this.searched = true
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    isSelected(u) {
      return this.selected.some((s) => s.id === u.id)
    },
    addMember(u) {
      if (this.isSelected(u)) return
      this.selected.push(u)
    },
    removeMember(u) {
      this.selected = this.selected.filter((s) => s.id !== u.id)
    },
    async submit() {
      if (!this.canSubmit) return
      this.submitting = true
      try {
        const res = await createGroup(
          this.groupName.trim(),
          this.selected.map((u) => u.id)
        )
        uni.showToast({ title: '群聊已创建', icon: 'none' })
        const name = encodeURIComponent(res.groupName || this.groupName.trim())
        setTimeout(() => {
          uni.redirectTo({
            url: `/pages/user/chat/chatRoom?sessionId=${res.id}&type=2&groupId=${res.groupId}&peerName=${name}`
          })
        }, 500)
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.group-create {
  min-height: 100vh;
  padding: 24rpx;
}

.group-create__field {
  margin-bottom: 32rpx;
}

.group-create__label {
  display: block;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.group-create__input {
  height: 76rpx;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  padding: 0 28rpx;
  font-size: 14px;
}

.group-create__placeholder {
  color: $text-sub;
}

.group-create__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.group-create__chip {
  background: rgba(107, 142, 90, 0.12);
  border-radius: $radius-btn;
  padding: 8rpx 24rpx;
}

.group-create__chip-text {
  color: $brand-primary;
  font-size: 13px;
}

.group-create__search {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.group-create__search .group-create__input {
  flex: 1;
}

.group-create__btn {
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 32rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
}

.group-create__btn-text {
  color: #ffffff;
  font-size: 14px;
}

.group-create__empty {
  text-align: center;
  color: $text-sub;
  font-size: 13px;
  padding: 32rpx 0;
}

.user-item {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 20rpx 24rpx;
  margin-bottom: 16rpx;
}

.user-item__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.user-item__body {
  flex: 1;
  min-width: 0;
}

.user-item__name {
  font-size: 14px;
  font-weight: 600;
}

.user-item__action {
  color: $brand-primary;
  font-size: 13px;
  flex-shrink: 0;

  &.is-added {
    color: $text-sub;
  }
}

.group-create__submit {
  margin-top: 48rpx;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
