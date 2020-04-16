package org.wordpress.android.imageeditor.preview

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER
import android.widget.ImageView.ScaleType.CENTER_CROP
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.preview_image_fragment.*
import org.wordpress.android.imageeditor.ImageEditor
import org.wordpress.android.imageeditor.ImageEditor.RequestListener
import org.wordpress.android.imageeditor.R
import org.wordpress.android.imageeditor.crop.CropViewModel.CropResult
import org.wordpress.android.imageeditor.preview.PreviewImageViewModel.ImageData
import org.wordpress.android.imageeditor.preview.PreviewImageViewModel.ImageLoadToFileState.ImageStartLoadingToFileState
import org.wordpress.android.imageeditor.utils.UiHelpers
import java.io.File

class PreviewImageFragment : Fragment() {
    private lateinit var viewModel: PreviewImageViewModel
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private var pagerAdapterObserver: PagerAdapterObserver? = null
    private lateinit var pageChangeCallback: OnPageChangeCallback

    private var cropActionMenu: MenuItem? = null

    private val imageDataList = listOf(
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/10/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/10/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/20/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/20/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/30/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/30/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/40/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/40/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/50/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/50/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/60/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/60/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/70/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/70/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/80/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/80/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/90/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/90/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/100/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/100/400/400.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/110/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/110/1000/1000.jpg",
            outputFileExtension = "jpg"
        ),
        ImageData(
            lowResImageUrl = "https://i.picsum.photos/id/120/200/200.jpg",
            highResImageUrl = "https://i.picsum.photos/id/120/400/400.jpg",
            outputFileExtension = "jpg"
        )
    )

    companion object {
        const val ARG_LOW_RES_IMAGE_URL = "arg_low_res_image_url"
        const val ARG_HIGH_RES_IMAGE_URL = "arg_high_res_image_url"
        const val ARG_OUTPUT_FILE_EXTENSION = "arg_output_file_extension"
        const val PREVIEW_IMAGE_REDUCED_SIZE_FACTOR = 0.1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.preview_image_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nonNullIntent = checkNotNull(requireActivity().intent)
        initializeViewModels(nonNullIntent)
        initializeViews()
    }

    private fun initializeViews() {
        initializeViewPager()
    }

    private fun initializeViewPager() {
        val previewImageAdapter = PreviewImageAdapter(
            loadIntoImageViewWithResultListener = { imageData, imageView, position ->
                loadIntoImageViewWithResultListener(imageData, imageView, position)
            }
        )
        previewImageViewPager.adapter = previewImageAdapter
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.onPageSelected(position)
            }
        }
        previewImageViewPager.registerOnPageChangeCallback(pageChangeCallback)

        val tabConfigurationStrategy = TabLayoutMediator.TabConfigurationStrategy { tab, position ->
            if (tab.customView == null) {
                val customView = LayoutInflater.from(context)
                        .inflate(R.layout.preview_image_thumbnail, thumbnailsTabLayout, false)
                tab.customView = customView
            }
            val imageView = (tab.customView as FrameLayout).findViewById<ImageView>(R.id.thumbnailImageView)
            val imageData = previewImageAdapter.currentList[position].data
            loadIntoImageView(imageData.lowResImageUrl, imageView)
        }

        tabLayoutMediator = TabLayoutMediator(
            thumbnailsTabLayout,
            previewImageViewPager,
            false,
            tabConfigurationStrategy
        )
        tabLayoutMediator.attach()

        pagerAdapterObserver = PagerAdapterObserver(
            thumbnailsTabLayout,
            previewImageViewPager,
            tabConfigurationStrategy
        )
        previewImageAdapter.registerAdapterDataObserver(pagerAdapterObserver as PagerAdapterObserver)

        // Setting page transformer explicitly sets internal RecyclerView's itemAnimator to null
        // to fix this issue: https://issuetracker.google.com/issues/37034191
        previewImageViewPager.setPageTransformer { _, _ ->
        }

        // Set adapter data before the ViewPager2.restorePendingState gets called
        // to avoid manual handling of the ViewPager2 state restoration.
        viewModel.uiState.value?.let {
            (previewImageViewPager.adapter as PreviewImageAdapter).submitList(it.peekContent().viewPagerItemsStates)
            cropActionMenu?.isEnabled = it.peekContent().editActionsEnabled
            UiHelpers.updateVisibility(thumbnailsTabLayout, it.peekContent().thumbnailsTabLayoutVisible)
        }
    }

    private fun initializeViewModels(nonNullIntent: Intent) {
        viewModel = ViewModelProvider(this).get(PreviewImageViewModel::class.java)
        setupObservers()
        // TODO: replace dummy list
        viewModel.onCreateView(imageDataList)
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this, Observer { uiStateEvent ->
            uiStateEvent?.getContentIfNotHandled()?.let { state ->
                (previewImageViewPager.adapter as PreviewImageAdapter).submitList(state.viewPagerItemsStates)
                cropActionMenu?.isEnabled = state.editActionsEnabled
                UiHelpers.updateVisibility(thumbnailsTabLayout, state.thumbnailsTabLayoutVisible)
            }
        })

        viewModel.loadIntoFile.observe(this, Observer { fileStateEvent ->
            fileStateEvent?.getContentIfNotHandled()?.let { fileState ->
                if (fileState is ImageStartLoadingToFileState) {
                    loadIntoFile(fileState.imageUrlAtPosition, fileState.position)
                }
            }
        })

        viewModel.navigateToCropScreenWithFileInfo.observe(this, Observer { fileInfoEvent ->
            fileInfoEvent?.getContentIfNotHandled()?.let { fileInfo ->
                navigateToCropScreenWithFileInfo(fileInfo)
            }
        })
    }

    private fun loadIntoImageView(url: String, imageView: ImageView) {
        ImageEditor.instance.loadIntoImageView(url, imageView, CENTER_CROP)
    }

    private fun loadIntoImageViewWithResultListener(imageData: ImageData, imageView: ImageView, position: Int) {
        ImageEditor.instance.loadIntoImageViewWithResultListener(
            imageData.highResImageUrl,
            imageView,
            CENTER,
            imageData.lowResImageUrl,
            object : RequestListener<Drawable> {
                override fun onResourceReady(resource: Drawable, url: String) {
                    viewModel.onLoadIntoImageViewSuccess(url, position)
                }

                override fun onLoadFailed(e: Exception?, url: String) {
                    viewModel.onLoadIntoImageViewFailed(url, position)
                }
            }
        )
    }

    private fun loadIntoFile(url: String, position: Int) {
        ImageEditor.instance.loadIntoFileWithResultListener(
            url,
            object : RequestListener<File> {
                override fun onResourceReady(resource: File, url: String) {
                    viewModel.onLoadIntoFileSuccess(resource.path, position)
                }

                override fun onLoadFailed(e: Exception?, url: String) {
                    viewModel.onLoadIntoFileFailed()
                }
            }
        )
    }

    private fun navigateToCropScreenWithFileInfo(fileInfo: Triple<String, String?, Boolean>) {
        val (inputFilePath, outputFileExtension, shouldReturnToPreviewScreen) = fileInfo

        val navOptions = if (!shouldReturnToPreviewScreen) {
            NavOptions.Builder().setPopUpTo(R.id.preview_dest, true).build()
        } else {
            null
        }

        val navController = findNavController()

        // TODO: Temporarily added if check to fix this occasional crash
        // https://stackoverflow.com/q/51060762/193545
        if (navController.currentDestination?.id == R.id.preview_dest) {
            navController.navigate(
                PreviewImageFragmentDirections.actionPreviewFragmentToCropFragment(
                    inputFilePath,
                    outputFileExtension,
                    shouldReturnToPreviewScreen
                ),
                navOptions
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_preview_fragment, menu)
        cropActionMenu = menu.findItem(R.id.menu_crop)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.menu_crop) {
        viewModel.onCropMenuClicked(previewImageViewPager.currentItem)
        true
    } else {
        super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerAdapterObserver?.let {
            previewImageViewPager.adapter?.unregisterAdapterDataObserver(it)
        }
        pagerAdapterObserver = null
        tabLayoutMediator.detach()
        previewImageViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }
}
