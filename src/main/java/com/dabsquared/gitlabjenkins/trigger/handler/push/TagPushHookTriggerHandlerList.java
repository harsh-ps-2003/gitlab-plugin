package com.dabsquared.gitlabjenkins.trigger.handler.push;

import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.MergeRequestLabelFilter;
import hudson.model.Job;
import java.util.List;
import org.gitlab4j.api.webhook.TagPushEvent;

class TagPushHookTriggerHandlerList implements TagPushHookTriggerHandler {

    private final List<TagPushHookTriggerHandler> handlers;

    TagPushHookTriggerHandlerList(List<TagPushHookTriggerHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void handle(
            Job<?, ?> job,
            TagPushEvent event,
            boolean ciSkip,
            BranchFilter branchFilter,
            MergeRequestLabelFilter mergeRequestLabelFilter) {
        for (TagPushHookTriggerHandler handler : handlers) {
            handler.handle(job, event, ciSkip, branchFilter, mergeRequestLabelFilter);
        }
    }
}