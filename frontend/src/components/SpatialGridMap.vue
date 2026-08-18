<template>
  <section class="spatial-map-shell" aria-label="网格真实空间地图">
    <div class="map-truth-note">
      <i class="el-icon-location-information" aria-hidden="true" />
      <span>
        地图只展示具备有效中心坐标的网格：已定位 {{ locatedGrids.length }} 个，
        待定位 {{ unlocatedGrids.length }} 个。未定位网格不会生成模拟点位。
      </span>
    </div>

    <div class="spatial-map-layout">
      <div class="spatial-map-stage">
        <div class="map-stage-badges" aria-hidden="true">
          <span><i class="el-icon-map-location" /> {{ locatedGrids.length }} 个真实点位</span>
          <span v-if="unlocatedGrids.length" class="is-warning">
            <i class="el-icon-warning-outline" /> {{ unlocatedGrids.length }} 个待补坐标
          </span>
        </div>
        <div ref="map" class="spatial-leaflet-map" aria-label="OpenStreetMap 网格点位地图" />
        <div v-if="tileError" class="map-tile-error" role="status">
          <i class="el-icon-warning-outline" />
          <span><strong>底图暂时无法加载</strong>真实坐标点位仍保留，请检查网络后重试。</span>
        </div>
        <div v-if="!locatedGrids.length" class="map-empty-overlay">
          <i class="el-icon-map-location" aria-hidden="true" />
          <strong>当前筛选下没有可绘制的真实点位</strong>
          <p>请在右侧待定位清单中补充中心经纬度。</p>
        </div>
      </div>

      <aside class="spatial-map-sidebar" aria-label="地图点位与待定位网格">
        <article v-if="selectedGrid" class="map-selection-detail" aria-live="polite">
          <header>
            <span>当前点位</span>
            <el-tag size="mini" :type="selectedGrid.status === 'ENABLED' ? 'success' : 'info'" effect="plain">
              {{ selectedGrid.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </header>
          <p>{{ selectedGrid.code }}</p>
          <h3>{{ selectedGrid.name }}</h3>
          <dl>
            <div><dt>所属社区</dt><dd>{{ selectedGrid.communityName }}</dd></div>
            <div><dt>中心坐标</dt><dd>{{ coordinateLabel(selectedGrid) }}</dd></div>
            <div><dt>地址说明</dt><dd>{{ display(selectedGrid.address) }}</dd></div>
          </dl>
          <div class="map-worker-list">
            <span v-for="worker in selectedGrid.workers" :key="worker.id">
              <i class="el-icon-user" /> {{ worker.name }}{{ worker.primary ? ' · 主责' : '' }}
            </span>
            <span v-if="!selectedGrid.workers.length" class="is-unassigned">
              <i class="el-icon-user" /> 暂未分配网格员
            </span>
          </div>
        </article>

        <section v-if="locatedGrids.length" class="map-record-group">
          <header><strong>已定位网格</strong><span>{{ locatedGrids.length }}</span></header>
          <div class="map-record-list">
            <button
              v-for="grid in locatedGrids"
              :key="grid.id"
              class="spatial-grid-record is-located"
              :class="{ 'is-selected': selectedGridId === grid.id }"
              type="button"
              @click="selectGrid(grid.id)"
            >
              <span class="record-status-dot" aria-hidden="true" />
              <span><strong>{{ grid.name }}</strong><small>{{ grid.code }} · {{ grid.communityName }}</small></span>
              <i class="el-icon-position" aria-hidden="true" />
            </button>
          </div>
        </section>

        <section class="map-record-group is-unlocated-group">
          <header><strong>待定位清单</strong><span>{{ unlocatedGrids.length }}</span></header>
          <p v-if="unlocatedGrids.length" class="unlocated-guidance">补充中心经纬度后，网格才会进入地图。</p>
          <div v-if="unlocatedGrids.length" class="map-record-list">
            <article v-for="grid in unlocatedGrids" :key="grid.id" class="spatial-grid-record is-unlocated">
              <span class="record-status-dot" aria-hidden="true" />
              <span><strong>{{ grid.name }}</strong><small>{{ grid.code }} · {{ grid.communityName }}</small></span>
              <el-button v-if="canManage" type="text" @click="$emit('edit', grid)">补坐标</el-button>
            </article>
          </div>
          <div v-else class="all-located-note">
            <i class="el-icon-circle-check" aria-hidden="true" /> 所有网格均已具备真实坐标
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

function validCoordinate(longitude, latitude) {
  if (
    longitude === null || longitude === undefined || String(longitude).trim() === '' ||
    latitude === null || latitude === undefined || String(latitude).trim() === ''
  ) return false
  const lng = Number(longitude)
  const lat = Number(latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90
}

export default {
  name: 'SpatialGridMap',
  props: {
    communities: { type: Array, default: () => [] },
    canManage: { type: Boolean, default: false }
  },
  data() {
    return {
      map: null,
      markerLayer: null,
      markers: new Map(),
      resizeObserver: null,
      selectedGridId: '',
      tileError: false
    }
  },
  computed: {
    grids() {
      return this.communities.flatMap(community => (community.grids || []).map(grid => ({
        ...grid,
        communityId: community.id,
        communityName: community.name,
        communityCode: community.code
      })))
    },
    locatedGrids() {
      return this.grids.filter(grid => validCoordinate(grid.centerLongitude, grid.centerLatitude))
    },
    unlocatedGrids() {
      return this.grids.filter(grid => !validCoordinate(grid.centerLongitude, grid.centerLatitude))
    },
    selectedGrid() {
      return this.locatedGrids.find(grid => grid.id === this.selectedGridId) || null
    }
  },
  watch: {
    communities: {
      deep: true,
      handler() {
        this.$nextTick(() => this.renderMarkers())
      }
    }
  },
  mounted() {
    this.initializeMap()
  },
  beforeDestroy() {
    if (this.resizeObserver) this.resizeObserver.disconnect()
    if (this.map) this.map.remove()
  },
  methods: {
    initializeMap() {
      this.map = L.map(this.$refs.map, {
        zoomControl: true,
        scrollWheelZoom: false,
        preferCanvas: true
      }).setView([35, 104], 4)
      this.map.attributionControl.setPrefix(false)
      const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'
      })
      tiles.on('tileerror', () => { this.tileError = true })
      tiles.on('tileload', () => { this.tileError = false })
      tiles.addTo(this.map)
      this.markerLayer = L.layerGroup().addTo(this.map)
      this.renderMarkers()
      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => this.map && this.map.invalidateSize(false))
        this.resizeObserver.observe(this.$refs.map)
      }
    },
    renderMarkers() {
      if (!this.map || !this.markerLayer) return
      this.markerLayer.clearLayers()
      this.markers.clear()
      const locatedIds = new Set(this.locatedGrids.map(grid => grid.id))
      if (!locatedIds.has(this.selectedGridId)) {
        this.selectedGridId = this.locatedGrids.length ? this.locatedGrids[0].id : ''
      }
      const bounds = []
      this.locatedGrids.forEach(grid => {
        const point = [Number(grid.centerLatitude), Number(grid.centerLongitude)]
        const marker = L.marker(point, {
          title: `${grid.name}，${grid.communityName}`,
          alt: grid.name,
          keyboard: true,
          icon: this.markerIcon(grid.id === this.selectedGridId)
        })
        marker.on('click', () => this.selectGrid(grid.id))
        marker.addTo(this.markerLayer)
        this.markers.set(grid.id, marker)
        bounds.push(point)
      })
      this.map.invalidateSize(false)
      if (bounds.length === 1) this.map.setView(bounds[0], 15)
      else if (bounds.length > 1) this.map.fitBounds(bounds, { padding: [44, 44], maxZoom: 16 })
      else this.map.setView([35, 104], 4)
    },
    markerIcon(selected) {
      return L.divIcon({
        className: `grid-leaflet-marker${selected ? ' is-selected' : ''}`,
        html: '<span aria-hidden="true"></span>',
        iconSize: [34, 42],
        iconAnchor: [17, 41]
      })
    },
    selectGrid(id) {
      this.selectedGridId = id
      this.markers.forEach((marker, markerId) => marker.setIcon(this.markerIcon(markerId === id)))
      const selected = this.locatedGrids.find(grid => grid.id === id)
      if (selected && this.map) {
        this.map.panTo([Number(selected.centerLatitude), Number(selected.centerLongitude)])
      }
    },
    coordinateLabel(grid) {
      return `${Number(grid.centerLongitude).toFixed(6)}, ${Number(grid.centerLatitude).toFixed(6)}`
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    }
  }
}
</script>
