<template>
  <div class="view-switcher" role="group" :aria-label="label">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="{ 'is-active': value === option.value }"
      :aria-pressed="value === option.value ? 'true' : 'false'"
      @click="$emit('input', option.value)"
    >
      <i v-if="option.icon" :class="option.icon" aria-hidden="true" />
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<script>
export default {
  name: 'ViewSwitcher',
  props: {
    value: { type: String, required: true },
    options: { type: Array, required: true },
    label: { type: String, default: '切换数据视图' }
  }
}
</script>
