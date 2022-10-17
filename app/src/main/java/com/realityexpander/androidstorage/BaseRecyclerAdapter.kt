package com.realityexpander.androidstorage

import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

abstract class BaseRecyclerAdapter<T> protected constructor()
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list: MutableList<T>

    init {
        list = ArrayList()
    }

    fun setList(list: MutableList<T>) {
        this.list = list
    }

    fun getItem(position: Int): T? {
        return if (position < list.size) {
            list[position]
        } else null
    }

    fun add(item: T) {
        val position = itemCount
        list.add(item)
        notifyItemInserted(position)
    }

    fun addAll(items: List<T>) {
        val position = itemCount
        list.addAll(items)
        notifyItemRangeInserted(position, items.size)
    }

    fun remove(item: T) {
        val position = list.indexOf(item)
        if (position > -1) {
            list.remove(item)
            notifyItemRemoved(position)
        }
    }

    fun clear() {
        val itemCount = itemCount
        list.clear()
        notifyItemRangeRemoved(0, itemCount)
    }

    fun notifyDataChanged() {
        notifyDataSetChanged()
    }

    fun clearAndAddAll(items: List<T>) {
        clear()
        addAll(items)
    }

    fun setListAndNotify(list: List<T>) {
        setList(list.toMutableList())
        notifyDataChanged()
    }


    override fun getItemCount(): Int {
        return list.size
    }
}
