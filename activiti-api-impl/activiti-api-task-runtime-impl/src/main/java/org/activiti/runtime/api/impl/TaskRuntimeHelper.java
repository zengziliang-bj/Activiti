package org.activiti.runtime.api.impl;

import org.activiti.api.runtime.shared.NotFoundException;
import org.activiti.api.runtime.shared.identity.UserGroupManager;
import org.activiti.api.runtime.shared.security.SecurityManager;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.payloads.CreateTaskVariablePayload;
import org.activiti.api.task.model.payloads.UpdateTaskPayload;
import org.activiti.api.task.model.payloads.UpdateTaskVariablePayload;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.persistence.entity.VariableInstance;
import org.activiti.runtime.api.model.impl.APITaskConverter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TaskRuntimeHelper {
    private final TaskService taskService;
    private final SecurityManager securityManager;
    private final UserGroupManager userGroupManager;
    private final APITaskConverter taskConverter;

    public TaskRuntimeHelper(TaskService taskService,
                             APITaskConverter taskConverter,
                             SecurityManager securityManager,
                             UserGroupManager userGroupManager) {
        this.taskService = taskService;
        this.securityManager = securityManager;
        this.userGroupManager = userGroupManager;
        this.taskConverter = taskConverter;
    }

    public Task applyUpdateTaskPayload(boolean isAdmin, UpdateTaskPayload updateTaskPayload) {

        org.activiti.engine.task.Task internalTask;

        if (isAdmin) {
            internalTask = getInternalTask(updateTaskPayload.getTaskId());
        } else {
            internalTask = getTaskToUpdate(updateTaskPayload.getTaskId());
        }

        int updates = updateName(updateTaskPayload,
                internalTask,
                0);
        updates = updateDescription(updateTaskPayload,
                internalTask,
                updates);
        updates = updatePriority(updateTaskPayload,
                internalTask,
                updates);

        updates = updateDueDate(updateTaskPayload,
                internalTask,
                updates);
        updates = updateParentTaskId(updateTaskPayload,
                internalTask,
                updates);
        updates = updateFormKey(updateTaskPayload,
                internalTask,
                updates);

        if (updates > 0) {
            taskService.saveTask(internalTask);
        }

        return taskConverter.from(getInternalTask(updateTaskPayload.getTaskId()));
    }

    private org.activiti.engine.task.Task getTaskToUpdate(String taskId) {

        org.activiti.engine.task.Task internalTask = getInternalTaskWithChecks(taskId);
        assertCanModifyTask(internalTask);
        return internalTask;
    }

    private void assertCanModifyTask(org.activiti.engine.task.Task internalTask) {
        String authenticatedUserId = getAuthenticatedUser();
        // validate that you are trying to update task where you are the assignee
        if (!Objects.equals(internalTask.getAssignee(), authenticatedUserId)) {
            throw new IllegalStateException("You cannot update a task where you are not the assignee");
        }
    }

    private int updateFormKey(UpdateTaskPayload updateTaskPayload,
                              org.activiti.engine.task.Task internalTask,
                              int updates) {
        String newValue;

        if ((newValue = updateTaskPayload.getFormKey()) != null) {
            String oldValue = internalTask.getFormKey();
            if (!Objects.equals(oldValue, newValue)) {
                updates++;
                internalTask.setFormKey(newValue);
            }
        }
        return updates;
    }

    private int updateParentTaskId(UpdateTaskPayload updateTaskPayload,
                                   org.activiti.engine.task.Task internalTask,
                                   int updates) {
        String newValue;

        if ((newValue = updateTaskPayload.getParentTaskId()) != null) {
            String oldValue = internalTask.getParentTaskId();
            if (!Objects.equals(oldValue, newValue)) {
                updates++;
                internalTask.setParentTaskId(newValue);
            }
        }
        return updates;
    }

    private int updateDueDate(UpdateTaskPayload updateTaskPayload,
                              org.activiti.engine.task.Task internalTask,
                              int updates) {
        if (updateTaskPayload.getDueDate() != null && !Objects.equals(internalTask.getDueDate(),
                updateTaskPayload.getDueDate())) {
            updates++;
            internalTask.setDueDate(updateTaskPayload.getDueDate());
        }
        return updates;
    }

    private int updatePriority(UpdateTaskPayload updateTaskPayload,
                               org.activiti.engine.task.Task internalTask,
                               int updates) {
        if (updateTaskPayload.getPriority() != null && internalTask.getPriority() != updateTaskPayload.getPriority()) {
            updates++;
            internalTask.setPriority(updateTaskPayload.getPriority());
        }
        return updates;
    }

    private int updateDescription(UpdateTaskPayload updateTaskPayload,
                                  org.activiti.engine.task.Task internalTask,
                                  int updates) {
        String newValue;

        if ((newValue = updateTaskPayload.getDescription()) != null) {
            String oldValue = internalTask.getDescription();
            if (!Objects.equals(oldValue, newValue)) {
                updates++;
                internalTask.setDescription(newValue);
            }
        }
        return updates;
    }

    private int updateName(UpdateTaskPayload updateTaskPayload,
                           org.activiti.engine.task.Task internalTask,
                           int updates) {
        String newValue;
        if ((newValue = updateTaskPayload.getName()) != null) {
            String oldValue = internalTask.getName();
            if (!Objects.equals(oldValue, newValue)) {
                updates++;
                internalTask.setName(newValue);
            }
        }
        return updates;
    }

    public org.activiti.engine.task.Task getInternalTaskWithChecks(String taskId) {
        String authenticatedUserId = getAuthenticatedUser();

        if (authenticatedUserId != null && !authenticatedUserId.isEmpty() && userGroupManager != null) {

            List<String> userRoles = userGroupManager.getUserRoles(authenticatedUserId);
            List<String> userGroups = userGroupManager.getUserGroups(authenticatedUserId);
            org.activiti.engine.task.Task task = taskService.createTaskQuery().taskCandidateOrAssigned(authenticatedUserId,
                    userGroups).taskId(taskId).singleResult();
            if (task == null) {
                throw new NotFoundException("Unable to find task for the given id: " + taskId + " for user: " + authenticatedUserId + " (with groups: " + userGroups + " & with roles: " + userRoles + ")");
            }

            return task;
        }
        throw new IllegalStateException("There is no authenticated user, we need a user authenticated to find tasks");
    }

    public void assertHasAccessToTask(String taskId) {
        getInternalTaskWithChecks(taskId);
    }

    private String getAuthenticatedUser() {
        return securityManager != null ? securityManager.getAuthenticatedUserId() : null;
    }

    public org.activiti.engine.task.Task getInternalTask(String taskId) {
        org.activiti.engine.task.Task internalTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (internalTask == null) {
            throw new NotFoundException("Unable to find task for the given id: " + taskId);
        }
        return internalTask;
    }

    public Map<String, org.activiti.engine.impl.persistence.entity.VariableInstance> getInternalTaskVariables(String taskId) {
        return taskService.getVariableInstancesLocal(taskId);
    }

    public void createVariable(boolean isAdmin,
                               CreateTaskVariablePayload createTaskVariablePayload) {
        if (!isAdmin) {
            assertCanModifyTask(getInternalTask(createTaskVariablePayload.getTaskId()));
        }

        if (createTaskVariablePayload.getName() == null || createTaskVariablePayload.getName().isEmpty()) {
            throw new IllegalStateException("You cannot create a variable without name");
        }

        assertVariableDoesNotExist(createTaskVariablePayload);

        taskService.setVariableLocal(createTaskVariablePayload.getTaskId(),
                createTaskVariablePayload.getName(),
                createTaskVariablePayload.getValue());
    }

    private void assertVariableDoesNotExist(CreateTaskVariablePayload createTaskVariablePayload) {
        Map<String, VariableInstance> variables = taskService.getVariableInstancesLocal(createTaskVariablePayload.getTaskId());

        if (variables != null && variables.containsKey(createTaskVariablePayload.getName())) {
            throw new IllegalStateException("Variable already exists");
        }
    }

    public void updateVariable(boolean isAdmin,
                               UpdateTaskVariablePayload updateTaskVariablePayload) {
        if (!isAdmin) {
            assertCanModifyTask(getInternalTask(updateTaskVariablePayload.getTaskId()));
        }

        if (updateTaskVariablePayload.getName() == null || updateTaskVariablePayload.getName().isEmpty()) {
            throw new IllegalStateException("You cannot update a variable without name");
        }

        assertVariableExists(updateTaskVariablePayload);

        taskService.setVariableLocal(updateTaskVariablePayload.getTaskId(),
                updateTaskVariablePayload.getName(),
                updateTaskVariablePayload.getValue());
    }

    private void assertVariableExists(UpdateTaskVariablePayload updateTaskVariablePayload) {
        Map<String, VariableInstance> variables = taskService.getVariableInstancesLocal(updateTaskVariablePayload.getTaskId());

        if (variables == null) {
            throw new IllegalStateException("Variable does not exist");
        }

        if (!variables.containsKey(updateTaskVariablePayload.getName())) {
            throw new IllegalStateException("Variable does not exist");
        }
    }
}
