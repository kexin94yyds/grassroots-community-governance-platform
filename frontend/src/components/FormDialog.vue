<template>
  <el-dialog
    :visible="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :before-close="requestClose"
    append-to-body
    @open="resetForm"
  >
    <p v-if="description" class="dialog-description">{{ description }}</p>
    <el-alert
      v-if="error"
      class="dialog-alert"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <el-form
      ref="form"
      :model="form"
      :rules="mergedRules"
      label-position="top"
      @submit.native.prevent="submit"
    >
      <el-row :gutter="16">
        <el-col
          v-for="field in visibleFields"
          :key="field.prop"
          :xs="24"
          :sm="field.span || 24"
        >
          <el-form-item :label="field.label" :prop="field.prop">
            <el-input
              v-if="!field.type || ['input', 'password', 'textarea'].includes(field.type)"
              v-model="form[field.prop]"
              :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'"
              :rows="field.rows || 3"
              :placeholder="field.placeholder || `请输入${field.label}`"
              :maxlength="field.maxlength"
              :show-word-limit="Boolean(field.maxlength && field.type === 'textarea')"
              :disabled="Boolean(field.disabled)"
              :autocomplete="field.autocomplete || 'off'"
              clearable
              @keyup.enter.native="field.type === 'textarea' ? null : submit()"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="form[field.prop]"
              :multiple="Boolean(field.multiple)"
              :placeholder="field.placeholder || `请选择${field.label}`"
              :disabled="Boolean(field.disabled)"
              clearable
              filterable
            >
              <el-option
                v-for="option in field.options || []"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
                :disabled="Boolean(option.disabled)"
              />
            </el-select>
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="form[field.prop]"
              type="date"
              value-format="yyyy-MM-dd"
              :placeholder="field.placeholder || `请选择${field.label}`"
              :disabled="Boolean(field.disabled)"
            />
            <el-date-picker
              v-else-if="field.type === 'datetime'"
              v-model="form[field.prop]"
              type="datetime"
              value-format="yyyy-MM-ddTHH:mm:ss"
              :placeholder="field.placeholder || `请选择${field.label}`"
              :disabled="Boolean(field.disabled)"
            />
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="form[field.prop]"
              :active-text="field.activeText || ''"
              :inactive-text="field.inactiveText || ''"
              :disabled="Boolean(field.disabled)"
            />
            <p v-if="field.help" class="form-help">{{ field.help }}</p>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <slot name="extra" />

    <div slot="footer" class="dialog-footer">
      <el-button :disabled="submitting" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ confirmText }}
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
function copy(value) {
  return JSON.parse(JSON.stringify(value || {}))
}

export default {
  name: 'FormDialog',
  props: {
    visible: { type: Boolean, required: true },
    title: { type: String, required: true },
    description: { type: String, default: '' },
    value: { type: Object, required: true },
    fields: { type: Array, required: true },
    rules: { type: Object, default: () => ({}) },
    submitting: { type: Boolean, default: false },
    error: { type: String, default: '' },
    confirmText: { type: String, default: '保存' },
    width: { type: String, default: '640px' }
  },
  data() {
    return {
      form: copy(this.value)
    }
  },
  computed: {
    visibleFields() {
      return this.fields.filter(field => field.visible !== false && (!field.show || field.show(this.form)))
    },
    mergedRules() {
      const defaults = {}
      this.visibleFields.forEach(field => {
        if (field.required) {
          defaults[field.prop] = [{
            required: true,
            message: field.requiredMessage || `${field.label}不能为空`,
            trigger: field.type === 'select' || field.type === 'date' || field.type === 'datetime' ? 'change' : 'blur'
          }]
        }
      })
      return { ...defaults, ...this.rules }
    }
  },
  watch: {
    value: {
      deep: true,
      handler(value) {
        this.form = copy(value)
      }
    }
  },
  methods: {
    resetForm() {
      this.form = copy(this.value)
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    requestClose() {
      if (this.submitting) return
      this.$emit('update:visible', false)
      this.$emit('close')
    },
    submit() {
      if (this.submitting) return
      this.$refs.form.validate(valid => {
        if (valid) this.$emit('submit', copy(this.form))
      })
    }
  }
}
</script>
