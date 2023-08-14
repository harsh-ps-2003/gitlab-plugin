package com.dabsquared.gitlabjenkins.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import com.dabsquared.gitlabjenkins.gitlab.api.impl.V4GitLabClientBuilder;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.util.BuildData;
import hudson.tasks.Notifier;
import hudson.util.Secret;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import jenkins.model.Jenkins;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.MergeRequest;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.junit.MockServerRule;

final class TestUtility {
    static final String GITLAB_CONNECTION_V4 = "GitLabV4";
    static final String BUILD_URL = "/build/123";
    static final String MERGE_COMMIT_SHA = "eKJ3wuqJT98Kc8TCcBK7oggLR1E9Bty7eqSHfSLT";
    static final int BUILD_NUMBER = 1;
    static final long PROJECT_ID = 3;
    static final long MERGE_REQUEST_ID = 1;
    static final long MERGE_REQUEST_IID = 2;

    private static final String API_TOKEN = "secret";

    static void setupGitLabConnections(JenkinsRule jenkins, MockServerRule mockServer) throws IOException {
        GitLabConnectionConfig connectionConfig = jenkins.get(GitLabConnectionConfig.class);
        String apiTokenId = "apiTokenId";
        for (CredentialsStore credentialsStore : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (credentialsStore instanceof SystemCredentialsProvider.StoreImpl) {
                List<Domain> domains = credentialsStore.getDomains();
                credentialsStore.addCredentials(
                        domains.get(0),
                        new StringCredentialsImpl(
                                CredentialsScope.SYSTEM,
                                apiTokenId,
                                "GitLab API Token",
                                Secret.fromString(TestUtility.API_TOKEN)));
            }
        }
        connectionConfig.addConnection(new GitLabConnection(
                TestUtility.GITLAB_CONNECTION_V4,
                "http://localhost:" + mockServer.getPort() + "/gitlab",
                apiTokenId,
                new V4GitLabClientBuilder(),
                false,
                10,
                10));
    }

    static <T extends Notifier & MatrixAggregatable> void verifyMatrixAggregatable(
            Class<T> publisherClass, BuildListener listener) throws InterruptedException, IOException {
        AbstractBuild build = mock(AbstractBuild.class);
        AbstractProject project = mock(MatrixConfiguration.class);
        Notifier publisher = mock(publisherClass);
        MatrixBuild parentBuild = mock(MatrixBuild.class);

        when(build.getParent()).thenReturn(project);
        when(((MatrixAggregatable) publisher).createAggregator(any(MatrixBuild.class), any(), any(BuildListener.class)))
                .thenCallRealMethod();
        when(publisher.perform(any(AbstractBuild.class), any(Launcher.class), any(BuildListener.class)))
                .thenReturn(true);

        MatrixAggregator aggregator = ((MatrixAggregatable) publisher).createAggregator(parentBuild, null, listener);
        aggregator.startBuild();
        aggregator.endBuild();
        verify(publisher).perform(parentBuild, null, listener);
    }

    static AbstractBuild mockSimpleBuild(String gitLabConnection, Result result, String... remoteUrls) {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildData buildData = mock(BuildData.class);
        when(buildData.getRemoteUrls()).thenReturn(new HashSet<>(Arrays.asList(remoteUrls)));
        when(build.getAction(BuildData.class)).thenReturn(buildData);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(BUILD_URL);
        when(build.getResult()).thenReturn(result);
        when(build.getNumber()).thenReturn(BUILD_NUMBER);

        AbstractProject<?, ?> project = mock(AbstractProject.class);
        when(project.getProperty(GitLabConnectionProperty.class))
                .thenReturn(new GitLabConnectionProperty(gitLabConnection));
        doReturn(project).when(build).getParent();
        doReturn(project).when(build).getProject();
        return build;
    }

    @SuppressWarnings("ConstantConditions")
    static String formatNote(AbstractBuild build, String note) {
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        return MessageFormat.format(
                note, build.getResult(), build.getParent().getDisplayName(), BUILD_NUMBER, buildUrl);
    }

    static <P extends MergeRequestNotifier> P preparePublisher(P publisher, AbstractBuild build)
            throws GitLabApiException {
        GitLabApi client = mock(GitLabApi.class);
        MergeRequestApi mergeRequestApi = mock(MergeRequestApi.class);
        P spyPublisher = spy(publisher);
        doReturn(mergeRequestApi).when(client).getMergeRequestApi();
        MergeRequest mergeRequest = new MergeRequest();
        mergeRequest.setId(MERGE_REQUEST_ID);
        mergeRequest.setIid(MERGE_REQUEST_IID);
        mergeRequest.setMergeCommitSha(MERGE_COMMIT_SHA);
        mergeRequest.setTitle("");
        mergeRequest.setSourceBranch("");
        mergeRequest.setTargetBranch("");
        mergeRequest.setSourceProjectId(PROJECT_ID);
        mergeRequest.setTargetProjectId(PROJECT_ID);
        mergeRequest.setDescription("");
        doReturn(mergeRequest).when(spyPublisher).getMergeRequest(build);
        return spyPublisher;
    }

    private TestUtility() {
        /* contains only static utility-methods */
    }
}
