package io.hkmario.monologue

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.hkmario.monologue.ui.AppViewModel
import io.hkmario.monologue.data.row
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {
    @Test fun rapidTypingAndTabChangesCannotPublishOldResults() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MonologueApp
        val store=ViewModelStore();lateinit var vm: AppViewModel
        instrumentation.runOnMainSync {vm=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AppViewModel::class.java]}
        try {
            runBlocking {app.graph.db.dao().putTracks(listOf(Track("search-1","Alpha","Beta","Gamma","Folder","file:test").row(),Track("search-2","Other","Delta","Epsilon","Else","file:test2").row()))}
            Thread.sleep(400)
            // A late Room emission must not hide the permission request on a fresh install.
            if(!vm.hasAudioPermission() && app.contentResolver.persistedUriPermissions.none {it.isReadPermission}) Assert.assertEquals(Phase.PermissionRequired,vm.state.value.library.phase)
            instrumentation.runOnMainSync {
                vm.dispatch(UiEvent.Query("Al"));vm.dispatch(UiEvent.Query("Alpha"));vm.dispatch(UiEvent.Tab(LibraryTab.Artists))
                Assert.assertTrue(vm.state.value.library.search.tracks.isEmpty())
            }
            Thread.sleep(350)
            Assert.assertEquals(LibraryTab.Artists,vm.state.value.library.search.request.tab)
            Assert.assertTrue(vm.state.value.library.search.tracks.isEmpty())
            Assert.assertTrue(vm.state.value.library.search.groups.isEmpty())
            instrumentation.runOnMainSync {vm.dispatch(UiEvent.Query("Beta"));vm.dispatch(UiEvent.Tab(LibraryTab.Albums));vm.dispatch(UiEvent.Query("Ｇａｍｍａ"))}
            Thread.sleep(350)
            Assert.assertEquals(LibraryTab.Albums,vm.state.value.library.search.request.tab)
            Assert.assertEquals("Gamma",vm.state.value.library.search.groups.single().title)
            Assert.assertTrue(vm.state.value.library.search.tracks.isEmpty())
        } finally {instrumentation.runOnMainSync {store.clear()}}
    }
}
