package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `taskList`. {@link Adf} accepts only these;
/// parsed documents may hold any child. Node types only, not marks, position, count or attributes.
public sealed interface TaskListContent extends AdfBlock
    permits BlockTaskItem, TaskItem, TaskList {}
