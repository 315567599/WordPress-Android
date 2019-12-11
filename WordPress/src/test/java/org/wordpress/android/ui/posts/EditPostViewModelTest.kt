package org.wordpress.android.ui.posts

import android.content.Context
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.InternalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.TEST_DISPATCHER
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.PostAction
import org.wordpress.android.fluxc.action.UploadAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.PostImmutableModel
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.fluxc.model.post.PostStatus.PENDING
import org.wordpress.android.fluxc.model.post.PostStatus.PRIVATE
import org.wordpress.android.fluxc.model.post.PostStatus.PUBLISHED
import org.wordpress.android.fluxc.model.post.PostStatus.SCHEDULED
import org.wordpress.android.fluxc.model.post.PostStatus.TRASHED
import org.wordpress.android.fluxc.model.post.PostStatus.UNKNOWN
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.ui.notifications.utils.PendingDraftsNotificationsUtilsWrapper
import org.wordpress.android.ui.posts.EditPostViewModel.UpdateFromEditor
import org.wordpress.android.ui.posts.EditPostViewModel.UpdateFromEditor.PostFields
import org.wordpress.android.ui.posts.EditPostViewModel.UpdateResult
import org.wordpress.android.ui.posts.editor.AztecEditorFragmentStaticWrapper
import org.wordpress.android.ui.uploads.UploadServiceFacade
import org.wordpress.android.ui.uploads.UploadUtilsWrapper
import org.wordpress.android.util.DateTimeUtilsWrapper
import org.wordpress.android.util.LocaleManagerWrapper
import org.wordpress.android.viewmodel.Event
import java.util.Calendar
import java.util.TimeZone

class EditPostViewModelTest : BaseUnitTest() {
    @Mock lateinit var dispatcher: Dispatcher
    @Mock lateinit var aztecEditorFragmentStaticWrapper: AztecEditorFragmentStaticWrapper
    @Mock lateinit var localeManagerWrapper: LocaleManagerWrapper
    @Mock lateinit var siteStore: SiteStore
    @Mock lateinit var uploadUtils: UploadUtilsWrapper
    @Mock lateinit var postUtils: PostUtilsWrapper
    @Mock lateinit var pendingDraftsNotificationsUtils: PendingDraftsNotificationsUtilsWrapper
    @Mock lateinit var uploadService: UploadServiceFacade
    @Mock lateinit var dateTimeUtils: DateTimeUtilsWrapper
    @Mock lateinit var postRepository: EditPostRepository
    @Mock lateinit var context: Context

    private lateinit var transactionCaptor: KArgumentCaptor<(PostModel) -> Boolean>
    private lateinit var updateResultCaptor: KArgumentCaptor<(PostModel) -> UpdateResult>
    private lateinit var actionCaptor: KArgumentCaptor<Action<PostModel>>

    private lateinit var viewModel: EditPostViewModel
    private val title = "title"
    private val updatedTitle = "updatedTitle"
    private val content = "content"
    private val updatedContent = "updatedContent"
    private val currentTime = "2019-11-10T11:10:00+0100"
    private val postStatus = "DRAFT"
    private val postModel = PostModel()
    private val site = SiteModel()
    private val localSiteId = 1
    private val immutablePost: PostImmutableModel = postModel
    private val postId = 2

    @InternalCoroutinesApi
    @Before
    fun setUp() {
        viewModel = EditPostViewModel(
                TEST_DISPATCHER,
                TEST_DISPATCHER,
                dispatcher,
                aztecEditorFragmentStaticWrapper,
                localeManagerWrapper,
                siteStore,
                uploadUtils,
                postUtils,
                pendingDraftsNotificationsUtils,
                uploadService,
                dateTimeUtils
        )
        transactionCaptor = argumentCaptor()
        updateResultCaptor = argumentCaptor()
        actionCaptor = argumentCaptor()
        setupCurrentTime()
        postModel.setTitle(title)
        postModel.setContent(content)
        postModel.setStatus(postStatus)
        whenever(postRepository.getEditablePost()).thenReturn(postModel)
        whenever(postRepository.content).thenReturn(content)
    }

    @Test
    fun `delays save call`() {
        var event: Event<Unit>? = null
        viewModel.onSavePostTriggered.observeForever {
            event = it
        }
        assertThat(event).isNull()

        viewModel.savePostWithDelay()

        assertThat(event).isNotNull()
    }

    @Test
    fun `sorts media IDs`() {
        viewModel.mediaMarkedUploadingOnStartIds = listOf("B", "A")

        viewModel.sortMediaMarkedUploadingOnStartIds()

        assertThat(viewModel.mediaMarkedUploadingOnStartIds).containsExactly("A", "B")
    }

    @Test
    fun `saves post to DB and updates media IDs for Aztec editor`() {
        assertThat(viewModel.mediaMarkedUploadingOnStartIds).isEmpty()
        val mediaIDs = listOf("A")
        whenever(
                aztecEditorFragmentStaticWrapper.getMediaMarkedUploadingInPostContent(
                        context,
                        content
                )
        ).thenReturn(
                mediaIDs
        )
        whenever(postRepository.postHasChangesFromDb()).thenReturn(true)

        viewModel.savePostToDb(context, postRepository, true)

        verify(dispatcher).dispatch(actionCaptor.capture())
        assertThat(actionCaptor.firstValue.type).isEqualTo(PostAction.UPDATE_POST)
        assertThat(actionCaptor.firstValue.payload).isEqualTo(postModel)
        assertThat(viewModel.mediaMarkedUploadingOnStartIds).isEqualTo(mediaIDs)
        verify(postRepository).saveDbSnapshot()
    }

    @Test
    fun `saves post to DB and does not update media IDs for non-Aztec editor`() {
        whenever(postRepository.postHasChangesFromDb()).thenReturn(true)

        viewModel.savePostToDb(context, postRepository, false)

        verify(dispatcher).dispatch(actionCaptor.capture())
        assertThat(actionCaptor.firstValue.type).isEqualTo(PostAction.UPDATE_POST)
        assertThat(actionCaptor.firstValue.payload).isEqualTo(postModel)
        assertThat(viewModel.mediaMarkedUploadingOnStartIds).isEmpty()
        verify(postRepository).saveDbSnapshot()
    }

    @Test
    fun `does not save the post with no change`() {
        whenever(postRepository.postHasChangesFromDb()).thenReturn(false)

        viewModel.savePostToDb(context, postRepository, false)

        verify(dispatcher, never()).dispatch(any())
        verify(postRepository, never()).saveDbSnapshot()
    }

    @Test
    fun `isCurrentMediaMarkedUploadingDifferentToOriginal is false on non-Aztec editor`() {
        val result = viewModel.isCurrentMediaMarkedUploadingDifferentToOriginal(context, false, "")

        assertThat(result).isFalse()
    }

    @Test
    fun `isCurrentMediaMarkedUploadingDifferentToOriginal is true when media IDs have changed`() {
        val currentList = listOf("A")
        val updatedList = listOf("B")
        viewModel.mediaMarkedUploadingOnStartIds = currentList
        whenever(
                aztecEditorFragmentStaticWrapper.getMediaMarkedUploadingInPostContent(
                        context,
                        content
                )
        ).thenReturn(
                updatedList
        )

        val result = viewModel.isCurrentMediaMarkedUploadingDifferentToOriginal(
                context,
                true,
                content
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `isCurrentMediaMarkedUploadingDifferentToOriginal is false when media IDs have not changed`() {
        val currentList = listOf("A")
        viewModel.mediaMarkedUploadingOnStartIds = currentList
        whenever(
                aztecEditorFragmentStaticWrapper.getMediaMarkedUploadingInPostContent(
                        context,
                        content
                )
        ).thenReturn(
                currentList
        )

        val result = viewModel.isCurrentMediaMarkedUploadingDifferentToOriginal(
                context,
                true,
                content
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `does not update post object with no change`() {
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) { PostFields(title, content) }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(false))
    }

    @Test
    fun `returns update error when post is missing`() {
        whenever(postRepository.hasPost()).thenReturn(false)

        val result = viewModel.updatePostObject(context, true, postRepository) {
            PostFields(
                    title,
                    content
            )
        }

        assertThat(result).isEqualTo(UpdateResult.Error)
    }

    @Test
    fun `returns update error when get content function returns null`() {
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) {
            UpdateFromEditor.Failed(
                    RuntimeException("Not found")
            )
        }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Error)
    }

    @Test
    fun `updates post title and date locally changed when title has changed`() {
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) {
            PostFields(
                    updatedTitle,
                    content
            )
        }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(true))
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
        assertThat(postModel.title).isEqualTo(updatedTitle)
        verify(postRepository).updatePublishDateIfShouldBePublishedImmediately(postModel)
    }

    @Test
    fun `updates post content and date locally changed when content has changed`() {
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) {
            PostFields(
                    title,
                    updatedContent
            )
        }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(true))
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
        assertThat(postModel.content).isEqualTo(updatedContent)
        verify(postRepository).updatePublishDateIfShouldBePublishedImmediately(postModel)
    }

    @Test
    fun `updates post date when media inserted on creation`() {
        viewModel.mediaInsertedOnCreation = true
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) { PostFields(title, content) }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(true))
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
        verify(postRepository).updatePublishDateIfShouldBePublishedImmediately(postModel)
        assertThat(viewModel.mediaInsertedOnCreation).isFalse()
    }

    @Test
    fun `updates post date when media list has changed`() {
        viewModel.mediaMarkedUploadingOnStartIds = listOf("A")
        whenever(
                aztecEditorFragmentStaticWrapper.getMediaMarkedUploadingInPostContent(
                        context,
                        content
                )
        ).thenReturn(listOf("B"))
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) { PostFields(title, content) }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(true))
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
        verify(postRepository).updatePublishDateIfShouldBePublishedImmediately(postModel)
    }

    @Test
    fun `updates post date when status has changed`() {
        whenever(postRepository.hasStatusChangedFromWhenEditorOpened(postStatus)).thenReturn(true)
        whenever(postRepository.hasPost()).thenReturn(true)

        viewModel.updatePostObject(context, true, postRepository) { PostFields(title, content) }

        verify(postRepository).updateInTransaction(updateResultCaptor.capture())

        val result = updateResultCaptor.firstValue.invoke(postModel)

        assertThat(result).isEqualTo(UpdateResult.Success(false))
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
    }

    @Test
    fun `savePostOnline changes post status to PENDING when user can't publish`() {
        val showAztecEditor = true
        val doFinishActivity = false
        val isFirstTimePublish = true
        listOf(UNKNOWN, PUBLISHED, SCHEDULED, PRIVATE).forEach { status ->
            reset(postRepository)
            setupPostRepository(status, userCanPublish = false)

            viewModel.savePostOnline(
                    isFirstTimePublish,
                    context,
                    postRepository,
                    showAztecEditor,
                    site,
                    doFinishActivity
            )

            verify(postRepository).updateStatus(PENDING)
        }
    }

    @Test
    fun `savePostOnline doesn't change status when user can't publish and post is DRAFT, PENDING or TRASHED`() {
        val showAztecEditor = true
        val doFinishActivity = false
        val isFirstTimePublish = true
        listOf(PostStatus.DRAFT, PENDING, TRASHED).forEach { status ->
            reset(postRepository)
            setupPostRepository(status, userCanPublish = false)

            viewModel.savePostOnline(
                    isFirstTimePublish,
                    context,
                    postRepository,
                    showAztecEditor,
                    site,
                    doFinishActivity
            )

            verify(postRepository, never()).updateStatus(any())
        }
    }

    @Test
    fun `savePostOnline saves post to DB when there are changes`() {
        val showAztecEditor = true
        val doFinishActivity = false
        val isFirstTimePublish = true

        setupPostRepository(PUBLISHED)
        whenever(postRepository.postHasChangesFromDb()).thenReturn(true)

        viewModel.savePostOnline(
                isFirstTimePublish,
                context,
                postRepository,
                showAztecEditor,
                site,
                doFinishActivity
        )
        verify(postRepository).saveDbSnapshot()

        verify(dispatcher).dispatch(actionCaptor.capture())
        assertThat(actionCaptor.firstValue.type).isEqualTo(PostAction.UPDATE_POST)
        assertThat(actionCaptor.firstValue.payload).isEqualTo(postModel)
    }

    @Test
    fun `savePostOnline does not save post to DB when there are no changes`() {
        val showAztecEditor = true
        val doFinishActivity = false
        val isFirstTimePublish = true

        setupPostRepository(PUBLISHED)
        whenever(postRepository.postHasChangesFromDb()).thenReturn(false)

        viewModel.savePostOnline(
                isFirstTimePublish,
                context,
                postRepository,
                showAztecEditor,
                site,
                doFinishActivity
        )

        verify(dispatcher, never()).dispatch(any())
    }

    @Test
    fun `savePostOnline uploads post online, tracks result and finishes`() {
        val showAztecEditor = true
        val doFinishActivity = true
        val isFirstTimePublish = true

        setupPostRepository(PUBLISHED)
        whenever(postRepository.postHasChangesFromDb()).thenReturn(false)
        var finished = false
        viewModel.onFinish.observeForever {
            it.applyIfNotHandled { finished = true }
        }

        viewModel.savePostOnline(
                isFirstTimePublish,
                context,
                postRepository,
                showAztecEditor,
                site,
                doFinishActivity
        )

        verify(postUtils).trackSavePostAnalytics(immutablePost, site)
        verify(uploadService).uploadPost(context, postId, isFirstTimePublish)
        verify(pendingDraftsNotificationsUtils).cancelPendingDraftAlarms(context, postId)
        assertThat(finished).isTrue()
    }

    @Test
    fun `savePostLocally sets locally changed flag when mediaInsertedOnCreation == true`() {
        val showAztecEditor = true
        val doFinishActivity = false
        setupPostRepository(PUBLISHED)
        whenever(postRepository.postHasEdits()).thenReturn(true)
        viewModel.mediaInsertedOnCreation = true
        setupCurrentTime()

        viewModel.savePostLocally(context, postRepository, showAztecEditor, doFinishActivity)

        assertThat(viewModel.mediaInsertedOnCreation).isFalse()

        assertThat(postModel.isLocallyChanged).isTrue()
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
    }

    @Test
    fun `savePostLocally sets locally changed flag when media marked uploading changed`() {
        val showAztecEditor = true
        val doFinishActivity = false
        whenever(postRepository.postHasEdits()).thenReturn(true)
        setupCurrentTime()
        val newContent = "new content"
        postModel.setContent(newContent)
        whenever(
                aztecEditorFragmentStaticWrapper.getMediaMarkedUploadingInPostContent(
                        context,
                        newContent
                )
        ).thenReturn(listOf("new media ID"))

        viewModel.savePostLocally(context, postRepository, showAztecEditor, doFinishActivity)

        verify(postRepository).updateInTransaction(transactionCaptor.capture())

        transactionCaptor.firstValue.invoke(postModel)

        assertThat(viewModel.mediaInsertedOnCreation).isFalse()

        assertThat(postModel.isLocallyChanged).isTrue()
        assertThat(postModel.dateLocallyChanged).isEqualTo(currentTime)
    }

    @Test
    fun `savePostLocally sets post status as cancelled and schedules notification`() {
        val showAztecEditor = true
        val doFinishActivity = false
        whenever(postRepository.postHasEdits()).thenReturn(true)
        whenever(postRepository.id).thenReturn(postId)
        whenever(postRepository.dateLocallyChanged).thenReturn(currentTime)

        viewModel.savePostLocally(context, postRepository, showAztecEditor, doFinishActivity)

        verify(dispatcher).dispatch(actionCaptor.capture())

        assertThat(actionCaptor.firstValue.type).isEqualTo(UploadAction.CANCEL_POST)
        verify(pendingDraftsNotificationsUtils).scheduleNextNotifications(
                context,
                postId,
                currentTime
        )
    }

    @Test
    fun `savePostLocally finishes when the flag is set`() {
        val showAztecEditor = true
        val doFinishActivity = true
        whenever(postRepository.postHasEdits()).thenReturn(false)
        var finish = false
        viewModel.onFinish.observeForever {
            it.applyIfNotHandled { finish = true }
        }

        viewModel.savePostLocally(context, postRepository, showAztecEditor, doFinishActivity)

        assertThat(finish).isTrue()
    }

    @Test
    fun `updates post and saves it asynchronously`() {
        val showAztecEditor = true
        var saved = false
        setupPostRepository(PUBLISHED)
        postModel.setContent("old content")
        postModel.setTitle("old title")

        viewModel.updateAndSavePostAsync(context, showAztecEditor, postRepository, {
            PostFields("updated title", "updated content")
        }, {
            saved = true
        })

        assertThat(saved).isTrue()
    }

    private fun setupPostRepository(
        postStatus: PostStatus,
        userCanPublish: Boolean = true
    ) {
        whenever(uploadUtils.userCanPublish(site)).thenReturn(userCanPublish)
        whenever(postRepository.status).thenReturn(postStatus)
        whenever(postRepository.getPost()).thenReturn(immutablePost)
        whenever(postRepository.hasPost()).thenReturn(true)
        whenever(postRepository.localSiteId).thenReturn(localSiteId)
        whenever(postRepository.id).thenReturn(postId)
        whenever(siteStore.getSiteByLocalId(localSiteId)).thenReturn(site)
        whenever(postRepository.updateInTransaction<Any>(any())).then {
            (it.arguments[0] as ((PostModel) -> Any)).invoke(postModel)
        }
    }

    private fun setupCurrentTime() {
        val now = Calendar.getInstance()
        now.set(2019, 10, 10, 10, 10, 0)
        now.timeZone = TimeZone.getTimeZone("UTC")
        whenever(localeManagerWrapper.getCurrentCalendar()).thenReturn(now)
        whenever(dateTimeUtils.currentTimeInIso8601UTC()).thenReturn(currentTime)
    }
}
