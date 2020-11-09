/**
 * Copyright 2020 SkillTree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package skills.metricsNew.builders.project


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import skills.controller.result.model.CountItem
import skills.metricsNew.builders.ProjectMetricsBuilder
import skills.metricsNew.builders.MetricsParams
import skills.services.AdminUsersService

@Component
class DistinctUsersOverTimeChartBuilderNew implements ProjectMetricsBuilder {

    @Autowired
    AdminUsersService adminUsersService

    @Override
    String getId() {
        return "distinctUsersOverTimeForProject"
    }

    @Override
    def build(String projectId, String chartId, Map<String, String> props) {
        Date start = MetricsParams.getStart(projectId, chartId, props)
        String skillId = props.containsKey(MetricsParams.P_SKILL_ID) ? MetricsParams.getSkillId(projectId, chartId, props) : null
        List<CountItem> dataItems = adminUsersService.getUsage(projectId, skillId, start)

        return dataItems;
    }
}
