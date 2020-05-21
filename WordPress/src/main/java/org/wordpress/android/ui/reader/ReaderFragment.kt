package org.wordpress.android.ui.reader

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import kotlinx.android.synthetic.main.reader_fragment_layout.*
import org.wordpress.android.R
import org.wordpress.android.R.string
import org.wordpress.android.WordPress
import org.wordpress.android.ui.WPWebViewActivity
import org.wordpress.android.ui.reader.ReaderTypes.ReaderPostListType
import org.wordpress.android.ui.reader.services.update.ReaderUpdateLogic.UpdateTask.FOLLOWED_BLOGS
import org.wordpress.android.ui.reader.services.update.ReaderUpdateLogic.UpdateTask.TAGS
import org.wordpress.android.ui.reader.services.update.ReaderUpdateServiceStarter
import org.wordpress.android.ui.reader.viewmodels.NewsCardViewModel
import org.wordpress.android.ui.reader.viewmodels.ReaderViewModel
import org.wordpress.android.ui.reader.viewmodels.ReaderViewModel.ReaderUiState
import java.util.EnumSet
import javax.inject.Inject

class ReaderFragment : Fragment(R.layout.reader_fragment_layout) {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: ReaderViewModel
    private lateinit var newsCardViewModel: NewsCardViewModel

    private val viewPagerCallback = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {
            // noop
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            // noop
        }

        override fun onPageSelected(position: Int) {
            viewModel.uiState.value?.let {
                val selectedTag = it.readerTagList[position]
                newsCardViewModel.onTagChanged(selectedTag)
                viewModel.onTagChanged(selectedTag)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity!!.application as WordPress).component().inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        initToolbar()
        initViewPager()
        initViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenInForeground()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenInBackground()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.reader_home, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_search) {
            viewModel.onSearchActionClicked()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun initToolbar() {
        toolbar.title = getString(string.reader_screen_title)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
    }

    private fun initViewPager() {
        view_pager.addOnPageChangeListener(viewPagerCallback)
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(ReaderViewModel::class.java)
        newsCardViewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)
                .get(NewsCardViewModel::class.java)
        startObserving()
    }

    private fun startObserving() {
        viewModel.uiState.observe(viewLifecycleOwner, Observer { uiState ->
            uiState?.let {
                updateTabs(uiState)
            }
        })

        viewModel.updateTags.observe(viewLifecycleOwner, Observer { updateAcion ->
            updateAcion?.getContentIfNotHandled()?.let {
                ReaderUpdateServiceStarter.startService(context, EnumSet.of(TAGS, FOLLOWED_BLOGS))
            }
        })

        viewModel.selectTab.observe(viewLifecycleOwner, Observer { selectTabAction ->
            selectTabAction.getContentIfNotHandled()?.let { tabPosition ->
                view_pager.currentItem = tabPosition
            }
        })

        viewModel.showSearch.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let {
                ReaderActivityLauncher.showReaderSearch(context)
            }
        })

        newsCardViewModel.openUrlEvent.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { url ->
                val activity: Activity? = activity
                if (activity != null) {
                    WPWebViewActivity.openURL(activity, url)
                }
            }
        })

        viewModel.start()
    }

    private fun updateTabs(uiState: ReaderUiState) {
        val adapter = TabsAdapter(childFragmentManager, uiState)
        view_pager.adapter = adapter
        tab_layout.setupWithViewPager(view_pager)
    }

    private class TabsAdapter(parent: FragmentManager, private val uiState: ReaderUiState) : FragmentStatePagerAdapter(
            parent,
            BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
    ) {
        override fun getItem(position: Int): Fragment {
            return ReaderPostListFragment.newInstanceForTag(
                    uiState.readerTagList[position],
                    ReaderPostListType.TAG_FOLLOWED,
                    true
            )
        }

        override fun getCount(): Int {
            return uiState.readerTagList.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return uiState.tabTitles[position]
        }
    }
}
