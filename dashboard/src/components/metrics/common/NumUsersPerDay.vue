/*
Copyright 2020 SkillTree

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
<template>
  <metrics-card :title="mutableTitle" data-cy="distinctNumUsersOverTime">
    <template v-slot:afterTitle>
      <span class="text-muted ml-2">|</span>
      <time-length-selector :options="timeSelectorOptions" @time-selected="updateTimeRange"/>
    </template>
    <metrics-overlay :loading="loading" :has-data="hasDataEnoughData" no-data-msg="This chart needs at least 2 days of user activity.">
      <apexchart type="area" height="350" :options="chartOptions" :series="distinctUsersOverTime" data-cy="apexchart"></apexchart>
    </metrics-overlay>
  </metrics-card>
</template>

<script>
  import dayjs from '@/common-components/DayJsCustomizer';
  import MetricsService from '../MetricsService';
  import numberFormatter from '@//filters/NumberFilter';
  import MetricsOverlay from '../utils/MetricsOverlay';
  import MetricsCard from '../utils/MetricsCard';
  import TimeLengthSelector from './TimeLengthSelector';

  export default {
    name: 'NumUsersPerDay',
    props: {
      title: {
        type: String,
        required: false,
        default: 'Users per day',
      },
    },
    components: { TimeLengthSelector, MetricsCard, MetricsOverlay },
    data() {
      return {
        loading: true,
        distinctUsersOverTime: [],
        hasDataEnoughData: false,
        mutableTitle: this.title,
        props: {
          start: dayjs().subtract(30, 'day').valueOf(),
        },
        timeSelectorOptions: [
          {
            length: 30,
            unit: 'days',
          },
          {
            length: 6,
            unit: 'months',
          },
          {
            length: 1,
            unit: 'year',
          },
        ],
        chartOptions: {
          chart: {
            type: 'area',
            stacked: false,
            height: 350,
            zoom: {
              type: 'x',
              enabled: true,
              autoScaleYaxis: true,
            },
            toolbar: {
              autoSelected: 'zoom',
              offsetY: -52,
            },
          },
          dataLabels: {
            enabled: false,
          },
          markers: {
            size: 0,
          },
          fill: {
            type: 'gradient',
            gradient: {
              shadeIntensity: 1,
              inverseColors: false,
              opacityFrom: 0.5,
              opacityTo: 0,
              stops: [0, 90, 100],
            },
          },
          yaxis: {
            labels: {
              formatter(val) {
                return numberFormatter(val);
              },
            },
            title: {
              text: 'Distinct # of Users',
            },
          },
          xaxis: {
            type: 'datetime',
          },
          tooltip: {
            shared: false,
            y: {
              formatter(val) {
                return numberFormatter(val);
              },
            },
          },
        },
      };
    },
    mounted() {
      // figure out if skillId is passed based on the context (page it's being loaded from)
      if (this.$route.params.skillId) {
        this.props.skillId = this.$route.params.skillId;
      } else if (this.$route.params.subjectId) {
        this.props.skillId = this.$route.params.subjectId;
      }
      this.loadData();
    },
    methods: {
      updateTimeRange(timeEvent) {
        if (this.$store.getters.config) {
          const oldestDaily = dayjs().subtract(this.$store.getters.config.maxDailyUserEvents, 'day');
          if (timeEvent.startTime < oldestDaily) {
            this.mutableTitle = 'Users per week';
          } else {
            this.mutableTitle = this.title;
          }
        }
        this.props.start = timeEvent.startTime.valueOf();
        this.loadData();
      },
      allZeros(data) {
        return data.filter((item) => item.count > 0).length === 0;
      },
      loadData() {
        this.loading = true;
        MetricsService.loadChart(this.$route.params.projectId, 'distinctUsersOverTimeForProject', this.props)
          .then((response) => {
            if (response && response.length > 1 && !this.allZeros(response)) {
              this.hasDataEnoughData = true;
              this.distinctUsersOverTime = [{
                data: response.map((item) => [item.value, item.count]),
                name: 'Users',
              }];
            } else {
              this.distinctUsersOverTime = [];
              this.hasDataEnoughData = false;
            }
            this.loading = false;
          });
      },
    },
  };
</script>

<style scoped>

</style>
