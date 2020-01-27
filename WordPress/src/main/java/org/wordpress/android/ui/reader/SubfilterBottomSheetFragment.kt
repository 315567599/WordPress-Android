package org.wordpress.android.ui.reader

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.*
import kotlinx.android.synthetic.main.wppref_view.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.ui.reader.services.update.ReaderUpdateLogic.UpdateTask
import org.wordpress.android.ui.reader.services.update.ReaderUpdateServiceStarter
import org.wordpress.android.ui.reader.subfilter.SubfilterCategory.SITES
import org.wordpress.android.ui.reader.subfilter.SubfilterCategory.TAGS
//import org.wordpress.android.ui.reader.subfilter.SubfilterCategoryListener
import org.wordpress.android.ui.reader.subfilter.SubfilterListItem.Site
import org.wordpress.android.ui.reader.subfilter.SubfilterListItem.Tag
import org.wordpress.android.ui.reader.subfilter.SubfilterPagerAdapter
import org.wordpress.android.ui.reader.subfilter.adapters.SubfilterListAdapter
import org.wordpress.android.ui.reader.viewmodels.ReaderPostListViewModel
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.NetworkUtils
import java.util.EnumSet
import javax.inject.Inject

class SubfilterBottomSheetFragment : BottomSheetDialogFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: ReaderPostListViewModel
    //@Inject lateinit var uiHelpers: UiHelpers
    //private val selectedTabListener: SubfilterCategoryListener
    //    get() = SubfilterCategoryListener(viewModel)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Handled_BottomSheetDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.subfilter_bottom_sheet, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        //val recyclerView = view.findViewById<RecyclerView>(R.id.filter_recycler_view)
        //recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        //recyclerView.adapter = SubfilterListAdapter(uiHelpers)
//
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(ReaderPostListViewModel::class.java)
//
        //viewModel.subFilters.observe(this, Observer {
        //    //(dialog.filter_recycler_view.adapter as? SubfilterListAdapter)?.update(it ?: listOf())
        //})



        //viewModel.loadSubFilters()
        val pager = view.findViewById<ViewPager>(R.id.view_pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        pager.adapter = SubfilterPagerAdapter(requireActivity(), childFragmentManager)
        tabLayout.setupWithViewPager(pager)
        //tabLayout.addOnTabSelectedListener(selectedTabListener)
        pager.currentItem = when(viewModel.getCurrentSubfilterValue()) {
            is Tag -> TAGS.ordinal
            else -> SITES.ordinal
        }

        viewModel.filtersMatchCount.observe(this, Observer {
            for (category in it.keys) {
                val tab = tabLayout.getTabAt(category.ordinal)
                tab?.let { sectionTab ->
                    sectionTab.text = "${view.context.getString(category.titleRes)} (${it[category]})"
                }
            }
        })
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        (requireActivity().applicationContext as WordPress).component().inject(this)
    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)
        viewModel.setIsBottomSheetShowing(false)
    }

   // @Subscribe(threadMode = ThreadMode.MAIN)
   // fun onEventMainThread(event: ReaderEvents.FollowedTagsChanged) {
   //     AppLog.d(T.READER, "Subfilter bottom sheet > followed tags changed")
   //     viewModel.loadSubFilters()
   // }
////
   //@Subscribe(threadMode = ThreadMode.MAIN)
   //fun onEventMainThread(event: ReaderEvents.FollowedBlogsChanged) {
   //    AppLog.d(T.READER, "Subfilter bottom sheet > followed blogs changed")
   //    viewModel.loadSubFilters()
   //}

    //private fun performUpdate() {
    //    performUpdate(
    //            EnumSet.of(
    //                    UpdateTask.TAGS,
    //                    UpdateTask.FOLLOWED_BLOGS
    //            )
    //    )
    //}
////
    //private fun performUpdate(tasks: EnumSet<UpdateTask>) {
    //    if (!NetworkUtils.isNetworkAvailable(activity)) {
    //        return
    //    }
////
    //    ReaderUpdateServiceStarter.startService(activity, tasks)
    //}
//
    //override fun onStart() {
    //    super.onStart()
    //    EventBus.getDefault().register(this)
    //}
////
    //override fun onStop() {
    //    EventBus.getDefault().unregister(this)
    //    super.onStop()
    //}
}
