<template>
  <el-dialog :title="$t('api_test.environment.environment_config')" :visible.sync="visible" class="environment-dialog" @close="close" :modal="false">
    <el-container v-loading="result.loading">
      <ms-aside-item :title="$t('api_test.environment.environment_list')" :data="environments" :item-operators="environmentOperators" :add-fuc="addEnvironment"
                     :delete-fuc="deleteEnvironment" @itemSelected="environmentSelected" ref="environmentItems"/>
      <environment-edit :environment="currentEnvironment" ref="environmentEdit" @close="close"/>
    </el-container>
  </el-dialog>
</template>

<script>
    import MsApiCollapse from "./collapse/ApiCollapse";
    import MsApiCollapseItem from "./collapse/ApiCollapseItem";
    import draggable from 'vuedraggable';
    import MsContainer from "../../../common/components/MsContainer";
    import MsAsideContainer from "../../../common/components/MsAsideContainer";
    import MsMainContainer from "../../../common/components/MsMainContainer";
    import MsAsideItem from "../../../common/components/MsAsideItem";
    import EnvironmentEdit from "./environment/EnvironmentEdit";

    export default {
      name: "ApiEnvironmentConfig",
      components: {
        EnvironmentEdit,
        MsAsideItem,
        MsMainContainer, MsAsideContainer, MsContainer, MsApiCollapseItem, MsApiCollapse, draggable},
      data() {
        return {
          result: {},
          visible:false,
          projectId: '',
          environments: [],
          currentEnvironment: {variables: [{}], headers: [{}], protocol: 'https', projectId: this.projectId},
          environmentOperators: [
            {
              icon: 'el-icon-document-copy',
              func: this.copyEnvironment
            },
            {
              icon: 'el-icon-delete',
              func: this.deleteEnvironment
            }
          ]
        }
      },
      methods: {
        open(projectId) {
          this.visible = true;
          this.projectId = projectId;
          this.getEnvironments();
        },
        deleteEnvironment(environment) {
          if (environment.id) {
            this.result = this.$get('/api/environment/delete/' + environment.id, () => {
              this.$success(this.$t('commons.delete_success'));
              this.getEnvironments();
            });
          }
        },
        copyEnvironment(environment) {
          let newEnvironment = {};
          Object.assign(newEnvironment, environment);
          newEnvironment.id = null;
          newEnvironment.name = this.getNoRepeatName(newEnvironment.name);
          this.$refs.environmentEdit._save(newEnvironment);
          this.environments.push(newEnvironment);
          this.$refs.environmentItems.itemSelected(this.environments.length -1 , newEnvironment);
        },
        getNoRepeatName(name) {
          for (let i in this.environments) {
            if (this.environments[i].name === name) {
              return this.getNoRepeatName(name + ' copy');
            }
          }
          return name;
        },
        addEnvironment() {
          let newEnvironment = this.getDefaultEnvironment();
          this.environments.push(newEnvironment);
          this.$refs.environmentItems.itemSelected(this.environments.length -1 , newEnvironment);
        },
        environmentSelected(environment) {
          this.getEnvironment(environment);
        },
        getEnvironments() {
          if (this.projectId) {
            this.result = this.$get('/api/environment/list/' + this.projectId, response => {
              this.environments = response.data;
              if (this.environments.length > 0) {
                this.$refs.environmentItems.itemSelected(0, this.environments[0]);
              } else {
                let item = this.getDefaultEnvironment();
                this.environments.push(item);
                this.$refs.environmentItems.itemSelected(0, item);
              }
            });
          }
        },
        getEnvironment(environment) {
          let item = environment;
          if (!(environment.variables instanceof Array)) {
            item.variables = JSON.parse(environment.variables);
          }
          if (!(environment.headers instanceof Array)) {
            item.headers = JSON.parse(environment.headers);
          }
          this.currentEnvironment =  item;
        },
        getDefaultEnvironment() {
          return {variables: [{}], headers: [{}], protocol: 'https', projectId: this.projectId};
        },
        close() {
          this.$emit('close');
          this.visible = false;
          this.$refs.environmentEdit.clearValidate();
        }
      }
    }
</script>

<style scoped>

  .environment-dialog >>> .el-dialog__body {
    padding-top: 20px;
  }

  .el-container {
    position: relative;
  }

  .ms-aside-container {
    height: 100%;
    position: absolute;
  }
</style>
