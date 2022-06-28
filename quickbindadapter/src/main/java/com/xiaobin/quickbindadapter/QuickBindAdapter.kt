package com.xiaobin.quickbindadapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.lang.Integer.min
import kotlin.math.abs

/**
 * @author 小斌
 * @data 2019/7/10
 */
class QuickBindAdapter() : RecyclerView.Adapter<BindHolder>() {

    private val TAG = "QuickBindAdapter"
    private val EMPTY_VIEW_TYPE = -1
    private val LOAD_MORE_TYPE = -2
    private val NONE_VIEW_TYPE = -3

    private var isHasMore = true

    //加载更多item样式
    var loadView: BaseLoadView<*>? = null

    val defaultLoadItem: DefaultLoadView by lazy {
        DefaultLoadView()
    }

    //空数据占位图
    var emptyView: BasePlaceholder<*, *, *>? = null

    val defaultPlaceholder: DefaultPlaceholder by lazy {
        DefaultPlaceholder()
    }

    var lifecycleOwner: LifecycleOwner? = null

    //是否允许列表数据不满一页时自动加载更多
    var canLoadMoreWhenNoFullContent = true

    //数据类型集合
    private val clazzList: MutableList<Class<*>> = ArrayList()

    //databinding属性名集合
    private val variableIds: MutableMap<Class<*>, Int> = HashMap()

    //item布局集合
    private val layoutIds: MutableMap<Class<*>, Int> = HashMap()

    //需要点击事件，长按事件监听的viewId集合
    private val clickListenerIds: MutableMap<Class<*>, List<Int>> = HashMap()
    private val longClickListenerIds: MutableMap<Class<*>, List<Int>> = HashMap()

    /**
     * 获得全部item数据
     *
     * @return 整个数据ArrayList
     */
    //数据集合
    private var listData: ItemData = ItemData()

    //额外的item样式处理
    var quickBind: QuickBind? = null

    /**
     * 设置加载更多监听
     * 如果没有配置自定义的加载更多样式，则初始化默认的
     */
    var onLoadMoreListener: OnLoadMoreListener? = null
        set(value) {
            field = value
            if (loadView == null) {
                loadView = defaultLoadItem
            }
            setupScrollListener()
        }
    var mRecyclerView: RecyclerView? = null

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy < 0 || mRecyclerView!!.layoutManager == null || dataCount == 0) {
                //下拉,没有layoutManger,无数据时不触发
                return
            }
            var lastItemIndex = 0
            when (val layoutManager = mRecyclerView!!.layoutManager) {
                is LinearLayoutManager -> {
                    if (layoutManager.childCount < 1) return
                    lastItemIndex = layoutManager.findLastVisibleItemPosition()
                }
                is GridLayoutManager -> {
                    if (layoutManager.childCount < 1) return
                    lastItemIndex = layoutManager.findLastVisibleItemPosition()
                }
                is StaggeredGridLayoutManager -> {
                    val positions = IntArray(layoutManager.spanCount)
                    layoutManager.findLastVisibleItemPositions(positions)
                    lastItemIndex = getTheBiggestNumber(positions)
                }
            }
            if (getItemViewType(lastItemIndex) == LOAD_MORE_TYPE) {
                if (dataCount == 0) {
                    if (loadView != null) {
                        loadView!!.isLoadMoreEnd()
                    }
                    return
                }
                //触发加载更多
                if (onLoadMoreListener != null && isHasMore && loadView != null) {
                    if (loadView!!.loadMoreState != BaseLoadView.LoadMoreState.LOADING_MORE) {
                        onLoadMoreListener!!.onLoadMore()
                    }
                    loadView!!.isLoadMore()
                }
            }
        }
    }

    constructor(lifecycleOwner: LifecycleOwner?) : this() {
        this.lifecycleOwner = lifecycleOwner
    }

    override fun getItemCount(): Int {
        if (emptyView != null && listData.size == 0) {
            return 1
        }
        return if (onLoadMoreListener != null && listData.size != 0) {
            //如果真数据大于0，并且有设置加载更多
            listData.size + 1
        } else listData.size
    }

    override fun getItemViewType(position: Int): Int {
        if (listData.size > 0 && onLoadMoreListener != null && position == itemCount - 1) {
            //如果设置了加载更多功能，则最后一个为加载更多的布局
            return LOAD_MORE_TYPE
        }
        if (emptyView != null && listData.size == 0) {
            return EMPTY_VIEW_TYPE
        }
        //得到itemData的index，然后得到对应的数据
        //判断数据类型集合中是否有这个数据的类型
        val itemData = getItemData(position) ?: return NONE_VIEW_TYPE
        val typeIndex = clazzList.indexOf(itemData.javaClass)
        return if (typeIndex >= 0) {
            //如果有这个类型，则返回这个类型所在集合的index
            typeIndex
        } else NONE_VIEW_TYPE
        //如果没有这个类型，则返回 NULL_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindHolder {
        //根据getItemViewType方法返回的viewType，判断需要用哪种布局
        if (viewType == LOAD_MORE_TYPE) {
            //加载更多布局
            return if (loadView != null) {
                loadView!!.createViewHolder(parent, lifecycleOwner)
            } else {
                BindHolder(View(parent.context))
            }
        } else if (viewType >= 0) {
            val mClass = clazzList[viewType]
            return if (StaggeredFullSpan::class.java.isAssignableFrom(mClass)) {
                FullSpanBindHolder(
                    DataBindingUtil.inflate(
                        LayoutInflater.from(parent.context),
                        layoutIds[mClass]!!, parent, false
                    ),
                    lifecycleOwner
                )
            } else {
                BindHolder(
                    DataBindingUtil.inflate(
                        LayoutInflater.from(parent.context),
                        layoutIds[mClass]!!, parent, false
                    ),
                    lifecycleOwner
                )
            }

        } else if (listData.size == 0) {
            return emptyView!!.createViewHolder(parent, lifecycleOwner)
        }
        return BindHolder(View(parent.context))
    }

    override fun onBindViewHolder(holder: BindHolder, position: Int) {
        if (listData.size == 0) return
        val itemType = holder.itemViewType
        if (itemType < 0) return
        val clz = clazzList[itemType]
        val itemData = getItemData(position)!!
        //item点击事件绑定
        if (onItemClickListener != null) {
            holder.itemView.setOnClickListener { view ->
                onItemClickListener?.onClick(this, view, itemData, position)
            }
        }
        //item长按事件绑定
        if (onItemLongClickListener != null) {
            holder.itemView.setOnLongClickListener { view ->
                onItemLongClickListener?.onLongClick(this, view, itemData, position) == true
            }
        }
        //子控件点击事件
        if (clickListenerIds.containsKey(clz)) {
            for (id in clickListenerIds[clz]!!) {
                holder.itemView.findViewById<View>(id).setOnClickListener { view ->
                    onItemChildClickListener?.onClick(this, view, itemData, position)
                }
            }
        }
        //子控件长按事件
        if (longClickListenerIds.containsKey(clz)) {
            for (id in longClickListenerIds[clz]!!) {
                holder.itemView.findViewById<View>(id).setOnLongClickListener { view ->
                    onItemChildLongClickListener?.onLongClick(
                        this,
                        view,
                        itemData,
                        position
                    ) == true
                }
            }
        }
        if (variableIds.containsKey(clz)) {
            holder.binding?.setVariable(variableIds[clz]!!, listData[position])
        }
        quickBind?.onBind(holder.binding!!, itemData, position)
        holder.binding?.executePendingBindings()
    }

    override fun onViewAttachedToWindow(holder: BindHolder) {
        super.onViewAttachedToWindow(holder)
        val lp = holder.itemView.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            if (holder.itemViewType == LOAD_MORE_TYPE
                || holder.itemViewType == EMPTY_VIEW_TYPE
                || holder is FullSpanBindHolder
            ) {
                lp.isFullSpan = true
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        mRecyclerView = recyclerView
        setupSpanSizeLookup()
        setupScrollListener()
    }

    /**
     * 配置SpanSizeLookup
     */
    private fun setupSpanSizeLookup() {
        mRecyclerView?.let { rv ->
            val layoutManager = rv.layoutManager
            if (layoutManager is GridLayoutManager) {
                val rvSpanSizeLookup = layoutManager.spanSizeLookup
                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (getItemViewType(position) == LOAD_MORE_TYPE ||
                            getItemViewType(position) == EMPTY_VIEW_TYPE
                        ) {
                            //空布局 or 加载更多布局
                            layoutManager.spanCount
                        } else {
                            rvSpanSizeLookup.getSpanSize(position)
                        }
                    }
                }
            } else if (layoutManager == null) {
                throw NullPointerException("请在完成初始化前先给RecyclerView设置LayoutManager！")
            }
        }
    }

    /**
     * 加载更多完成
     */
    fun loadMoreComplete() {
        if (dataCount == 0 || loadView == null || !isHasMore) return
        loadView!!.isLoadMoreSuccess()
    }

    /**
     * 加载更多完成，没有更多数据了
     */
    fun loadMoreEnd() {
        if (dataCount == 0 || loadView == null) return
        isHasMore = false
        loadView!!.isLoadMoreEnd()
    }

    /**
     * 加载更多失败了
     */
    fun loadMoreFail() {
        if (dataCount == 0 || loadView == null || !isHasMore) return
        loadView!!.isLoadMoreFail()
    }

    private fun setupScrollListener() {
        if (onLoadMoreListener == null) return
        //先移除之前的，在添加，防止重复添加
        mRecyclerView?.removeOnScrollListener(onScrollListener)
        mRecyclerView?.addOnScrollListener(onScrollListener)
    }

    private fun getTheBiggestNumber(numbers: IntArray?): Int {
        if (numbers == null || numbers.isEmpty()) {
            return -1
        }
        return numbers.max()
    }

    /**
     * 设置生命周期所有者
     * 用于特定用途，这个lifecycleOwner会给到每一个item
     *
     * @param lifecycleOwner
     */
    fun setLifecycleOwner(lifecycleOwner: LifecycleOwner?): QuickBindAdapter {
        this.lifecycleOwner = lifecycleOwner
        return this
    }

    /**
     * 移除空数据占位图
     */
    fun removeEmptyView() {
        emptyView = null
    }

    /**
     * 检查RV是否可以滑动，如果不可滑动(视为数据没有占满一页的情况)，并且开启了不满一页也可触发加载更多
     * 则触发加载更多回调
     */
    private fun checkScrollState() {
        if (mRecyclerView != null && dataCount > 0 && canLoadMoreWhenNoFullContent
            && onLoadMoreListener != null && loadView != null
        ) {
            mRecyclerView!!.post {
                if (mRecyclerView!!.layoutManager == null) return@post
                if (mRecyclerView!!.layoutManager!!.childCount == itemCount) {
                    if (!mRecyclerView!!.canScrollHorizontally(1) ||
                        !mRecyclerView!!.canScrollHorizontally(-1) ||
                        !mRecyclerView!!.canScrollVertically(1) ||
                        !mRecyclerView!!.canScrollVertically(-1)
                    ) {
                        //四个方向都不能滑动
                        if (loadView!!.loadMoreState != BaseLoadView.LoadMoreState.LOADING_MORE) {
                            if (getItemViewType(itemCount - 1) == LOAD_MORE_TYPE) {
                                if (onLoadMoreListener != null && isHasMore) {
                                    loadView!!.isLoadMore()
                                    onLoadMoreListener!!.onLoadMore()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 设置新的数据
     *
     * @param data 全新数据
     */
    fun setNewData(data: List<*>?) {
        listData.clear()
        data?.let {
            listData.addAll(it)
        }
        isHasMore = listData.size != 0
        notifyDataSetChanged()
        checkScrollState()
        if (listData.isEmpty()) {
            emptyView?.setPlaceholderAction(PlaceholderAction.ShowEmptyPage)
        }
    }

    /**
     * 设置新的数据
     *
     * @param data 全新数据
     */
    fun setNewData(data: ItemData?) {
        listData.clear()
        data?.let {
            listData = it
        }
        isHasMore = listData.size != 0
        notifyDataSetChanged()
        checkScrollState()
        if (listData.isEmpty()) {
            emptyView?.setPlaceholderAction(PlaceholderAction.ShowEmptyPage)
        }
    }

    /**
     * 移动Item
     *
     * @param fromPosition 要移动的Item下标
     * @param toPosition   要移动到的位置下标
     */
    fun movedPositions(fromPosition: Int, toPosition: Int) {
        listData.add(toPosition, listData.removeAt(fromPosition)) //数据更换
        notifyItemMoved(fromPosition, toPosition)
        notifyItemRangeChanged(
            min(fromPosition, toPosition),
            abs(fromPosition - toPosition) + 1
        )
    }

    /**
     * 添加单个数据
     *
     * @param data 单个数据，添加到最后
     */
    fun addData(data: Any) {
        listData.add(data)
        notifyItemInserted(listData.size)
        compatibilityDataSizeChanged(1)
        checkScrollState()
    }

    /**
     * 插入单个数据
     *
     * @param data  单个数据
     * @param index 插入位置
     */
    fun insertData(index: Int, data: Any) {
        listData.add(index, data)
        notifyItemInserted(index)
        notifyItemRangeChanged(index, listData.size - index)
        compatibilityDataSizeChanged(1)
    }

    /**
     * 插入多个数据
     *
     * @param datas 多个数据
     * @param index 插入位置
     */
    fun insertDatas(index: Int, datas: ItemData) {
        this.listData.addAll(index, datas)
        notifyItemRangeChanged(index, datas.size - index)
        compatibilityDataSizeChanged(datas.size)
    }

    /**
     * 插入多个数据
     *
     * @param datas 多个数据
     * @param index 插入位置
     */
    fun insertDatas(index: Int, datas: List<*>) {
        this.listData.addAll(index, datas)
        notifyItemRangeChanged(index, itemCount - index)
        compatibilityDataSizeChanged(datas.size)
    }

    /**
     * 添加数据
     *
     * @param datas 多个数据，添加到最后
     */
    fun addDatas(datas: List<*>) {
        val lastIndex = itemCount
        this.listData.addAll(datas)
        notifyItemRangeInserted(lastIndex - 1, datas.size)
        compatibilityDataSizeChanged(datas.size)
        checkScrollState()
    }

    /**
     * 添加数据
     *
     * @param datas 多个数据，添加到最后
     */
    fun addDatas(datas: ItemData) {
        val lastIndex = itemCount
        this.listData.addAll(datas)
        notifyItemRangeInserted(lastIndex - 1, datas.size)
        compatibilityDataSizeChanged(datas.size)
        checkScrollState()
    }

    /**
     * 移除某个item
     *
     * @param position 位置
     */
    fun remove(position: Int) {
        if (listData.size <= position) {
            return
        }
        listData.removeAt(position)
        notifyItemRemoved(position)
        compatibilityDataSizeChanged(0)
        notifyItemRangeChanged(position, listData.size - position)
        if (listData.isEmpty()) {
            emptyView?.setPlaceholderAction(PlaceholderAction.ShowEmptyPage)
        }
    }

    /**
     * 清空数据
     */
    fun removeAll() {
        listData.clear()
        notifyDataSetChanged()
        emptyView?.setPlaceholderAction(PlaceholderAction.ShowEmptyPage)
    }

    /**
     * 替换item内容
     *
     * @param position 位置
     * @param itemData 单个数据
     */
    fun replace(position: Int, itemData: Any) {
        if (listData.size <= position) {
            addData(itemData)
            return
        }
        listData[position] = itemData
        notifyItemChanged(position)
    }

    /**
     * 绑定布局
     *
     * @param clazz          数据类型
     * @param layoutId       布局ID
     * @param bindVariableId DataBinding BR
     * @return 这个对象
     */
    fun bind(clazz: Class<*>, @LayoutRes layoutId: Int, bindVariableId: Int): QuickBindAdapter {
        if (!clazzList.contains(clazz)) {
            clazzList.add(clazz)
        }
        layoutIds[clazz] = layoutId
        variableIds[clazz] = bindVariableId
        return this
    }

    /**
     * 绑定布局
     *
     * @param clazz    数据类型
     * @param layoutId 布局ID
     * @return 这个对象
     */
    fun bind(clazz: Class<*>, @LayoutRes layoutId: Int): QuickBindAdapter {
        if (!clazzList.contains(clazz)) {
            clazzList.add(clazz)
        }
        layoutIds[clazz] = layoutId
        return this
    }

    /**
     * 添加子控件点击监听
     *
     * @param clazz  数据类型
     * @param viewId 控件ID，多个
     * @return 这个对象
     */
    fun addClickListener(clazz: Class<*>, @IdRes vararg viewId: Int): QuickBindAdapter {
        val ids: MutableList<Int> = ArrayList(viewId.size)
        for (id in viewId) {
            ids.add(id)
        }
        clickListenerIds[clazz] = ids
        return this
    }

    /**
     * 添加子控件长按监听
     *
     * @param clazz  数据类型
     * @param viewId 控件ID，多个
     * @return 这个对象
     */
    fun addLongClickListener(clazz: Class<*>, @IdRes vararg viewId: Int): QuickBindAdapter {
        val ids: MutableList<Int> = ArrayList(viewId.size)
        for (id in viewId) {
            ids.add(id)
        }
        longClickListenerIds[clazz] = ids
        return this
    }

    /**
     * 获取指定item内容
     *
     * @param position 位置
     * @return 这个位置的数据
     */
    fun getItemData(position: Int): Any? {
        return listData[position]
    }

    /**
     * 获取数据大小
     *
     * @return 数据大小
     */
    val dataCount: Int
        get() = listData.size

    //*******************************用于外部调用的方法 结束******************************
    /**
     * 如果变动的数据大小和实际数据大小一致，则刷新整个列表
     *
     * @param size 变动的数据大小
     */
    private fun compatibilityDataSizeChanged(size: Int) {
        val dataSize = listData.size
        if (dataSize == size) {
            notifyDataSetChanged()
        }
    }

    /**
     * item事件
     */
    var onItemClickListener: OnClickListener? = null
    var onItemLongClickListener: OnLongClickListener? = null

    /**
     * 子控件事件
     */
    var onItemChildClickListener: OnClickListener? = null
    var onItemChildLongClickListener: OnLongClickListener? = null

    /**
     * 点击事件
     */
    interface OnClickListener {
        fun onClick(adapter: QuickBindAdapter, view: View, data: Any, position: Int)
    }

    /**
     * 长按事件
     */
    interface OnLongClickListener {
        fun onLongClick(
            adapter: QuickBindAdapter,
            view: View,
            data: Any,
            position: Int
        ): Boolean
    }

    /**
     * 加载更多
     */
    interface OnLoadMoreListener {
        fun onLoadMore()
    }

}