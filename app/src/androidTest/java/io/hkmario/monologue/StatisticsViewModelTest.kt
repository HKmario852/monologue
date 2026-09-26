package io.hkmario.monologue

import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class StatisticsViewModelTest {
    @Test fun roomEventsFeedIndependentAllTimeAndMonthlyDetails()=runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext.applicationContext as MonologueApp
        val id="stats-qa-${java.util.UUID.randomUUID()}";val dao=app.graph.db.dao();val store=ViewModelStore();lateinit var vm: AppViewModel
        val now=System.currentTimeMillis();val start=now-10000
        dao.putTrack(Track(id,"統計驗證","測試歌手",uri="",source=Source.Online).row())
        dao.event(ListenEvent("$id-time",id,id,start,now,10000,false))
        dao.event(ListenEvent("$id-count",id,id,now,now,0,true))
        try {
            inst.runOnMainSync {vm=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AppViewModel::class.java]}
            val state=withTimeout(10000) {vm.state.first {s->s.stats.all.any {it.track.id==id}}}
            assertEquals(10000L,state.stats.all.single {it.track.id==id}.listenedMs)
            assertEquals(1,state.stats.all.single {it.track.id==id}.count)
            inst.runOnMainSync {vm.dispatch(UiEvent.Statistics(Period.Month,-1))}
            withTimeout(5000) {vm.state.first {it.stats.offset== -1}}
            assertTrue(vm.state.value.stats.detail.none {it.track.id==id})
            assertTrue(vm.state.value.stats.all.any {it.track.id==id})
            assertEquals(Period.Week,vm.state.value.leaderboard.period)
        } finally {
            inst.runOnMainSync {store.clear()}
            dao.deleteEvents(listOf("$id-time","$id-count"))
            app.graph.db.openHelper.writableDatabase.execSQL("DELETE FROM tracks WHERE id=?",arrayOf(id))
        }
    }
}
