package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import io.hkmario.monologue.domain.Period

class CoreTest {
    @Test fun vinylPausesAndResumesWithoutReset() {
        val c=VinylClock();c.configure(0,playing=true,visible=true)
        assertEquals(90.0,c.angle(6_000_000_000),.0001)
        c.configure(6_000_000_000,playing=false)
        assertEquals(90.0,c.angle(30_000_000_000),.0001)
        c.configure(30_000_000_000,playing=true)
        assertEquals(105.0,c.angle(31_000_000_000),.0001)
    }
    @Test fun vinylInvisibleTimeIsNeverReplayed() {
        val c=VinylClock(22.0);c.configure(0,playing=true,visible=true)
        c.configure(2_000_000_000,visible=false)
        assertEquals(52.0,c.angle(900_000_000_000),.0001)
        c.configure(900_000_000_000,visible=true)
        assertEquals(67.0,c.angle(901_000_000_000),.0001)
    }
    @Test fun vinylCrossTrackBufferingAndReducedMotion() {
        val c=VinylClock();c.configure(0,playing=true,visible=true)
        // A new track cannot reset the session clock: no track ID exists in its input.
        c.configure(1_000_000_000,playing=false)
        c.configure(4_000_000_000,playing=false)
        assertEquals(15.0,c.angle(5_000_000_000),.0001)
        c.configure(6_000_000_000,playing=true)
        c.configure(7_000_000_000,allowed=false)
        assertEquals(30.0,c.angle(8_000_000_000),.0001)
        c.restore(123.0);assertNull(c.state.anchorNanos)
        c.configure(10_000_000_000,visible=true,allowed=true)
        assertEquals(138.0,c.angle(11_000_000_000),.0001)
    }
    @Test fun vinylMonotonicClockHasNoFrameAccumulationDrift() {val c=VinylClock();c.configure(0,true,true);assertEquals(90.0,c.angle(24_000_000_000L*10_000+6_000_000_000),.001)}
    private val tracks=listOf(Track("1","Ａｆｔｅｒｇｌｏｗ","Mira Sol","Evening","Music/Night","file:a"),Track("2","Ocean","Northline","Afterglow","Music/Day","file:b"))
    @Test fun tracksSearchMatchesArtistAndAlbumButReturnsOnlyTracks() {val s=search(tracks,SearchRequest(LibraryTab.Tracks,"afterglow",1));assertEquals(2,s.tracks.size);assertTrue(s.groups.isEmpty())}
    @Test fun artistSearchDoesNotReturnSongOrAlbumMatches() {val s=search(tracks,SearchRequest(LibraryTab.Artists,"afterglow",2));assertTrue(s.groups.isEmpty());assertTrue(s.tracks.isEmpty())}
    @Test fun albumAndFolderSearchRemainSeparate() {assertEquals(1,search(tracks,SearchRequest(LibraryTab.Albums,"afterglow",3)).groups.size);assertEquals(0,search(tracks,SearchRequest(LibraryTab.Folders,"afterglow",4)).groups.size)}
    @Test fun unicodeNormalizationAndHistoryDeduplication() {assertEquals("abc 音樂",normalize("ＡＢＣ　音樂"));val h=recordSearch(listOf("ＡＢＣ","old"),"abc");assertEquals(listOf("abc","old"),h);assertEquals(10,recordSearch((1..20).map{it.toString()},"new").size)}
    @Test fun lrcHandlesMultipleTagsOffsetAndRepeatedLines() {val l=Lrc.parse("[offset:-500]\n[00:01.2][00:03.45]歌詞\n[00:03.45]重複\n[ar:歌手]\n純文字");assertEquals(4,l.size);assertEquals(700L,l[0].timeMs);assertEquals(2950L,l[1].timeMs);assertNull(l.last().timeMs)}
    @Test fun translationsNeverUseIndexOrAmbiguousTimestamp() {
        val original=Lrc.parse("[00:10.00]first\n[00:20.00]second")
        val wrong=Lrc.parse("[00:11.00]錯一\n[00:25.00]錯二")
        assertTrue(Lrc.align(original,wrong).all {it.translation==null})
        val ok=Lrc.parse("[00:10.10]第一\n[00:20.20]第二")
        assertEquals("第一",Lrc.align(original,ok)[0].translation)
        assertNull(Lrc.align(original,Lrc.parse("[00:10.00]A\n[00:10.10]B"))[0].translation)
    }
    @Test fun mondayAndMonthAreEndExclusiveAndHistorySurvives() {
        val zone=ZoneId.of("Asia/Hong_Kong");val now=Instant.parse("2026-09-27T16:00:00Z")
        val week=periodRange(Period.Week,0,zone,now)
        assertEquals(now,week.start);assertEquals(Instant.parse("2026-10-04T16:00:00Z"),week.endExclusive)
        assertEquals(now,periodRange(Period.Week,-1,zone,now).endExclusive)
        val month=periodRange(Period.Month,0,zone,Instant.parse("2026-09-30T16:00:00Z"));assertEquals(Instant.parse("2026-09-30T16:00:00Z"),month.start)
    }
    @Test fun slicesSplitAtPeriodBoundary() {assertEquals(1000L,overlapMs(500,2500,0,1500));assertEquals(1000L,overlapMs(500,2500,1500,3000));assertEquals(0L,overlapMs(500,1500,1500,2000))}
    @Test fun dstWeekUsesCalendarBoundaries() {val r=periodRange(Period.Week,0,ZoneId.of("America/New_York"),Instant.parse("2026-03-05T12:00:00Z"));assertEquals(167L,Duration.between(r.start,r.endExclusive).toHours())}
    @Test fun countThresholdAndNoPauseBufferSeekInflation() {
        val meter=ListeningMeter();meter.update(0,1000,false);meter.update(10000,11000,true);meter.update(50000,51000,false)
        assertEquals(10000L,meter.totalMs);assertFalse(meter.markCount(120000))
        // Media position can jump arbitrarily; only monotonic elapsed time is accepted.
        meter.update(70000,71000,true);assertTrue(meter.markCount(120000));assertFalse(meter.markCount(120000))
        assertEquals(5000L,countThreshold(10000));assertEquals(30000L,countThreshold(0));assertEquals(240000L,listenBrainzThreshold(900000))
    }
    @Test fun downloadPauseIsOnlyBetweenItemsAndFinalItemFinishes() {assertEquals(DownloadPhase.Waiting,DownloadMachine.afterItem(1,true));assertEquals(DownloadPhase.Complete,DownloadMachine.afterItem(0,true));assertEquals(DownloadPhase.Waiting,DownloadMachine.toggle(DownloadPhase.Waiting,false));assertEquals(DownloadPhase.Cancelled,DownloadMachine.afterItem(0,true,true))}
    @Test fun retryOnlyFailedItems() {val items=listOf(DownloadItem("1","1","ok",DownloadStatus.Complete),DownloadItem("2","2","bad",DownloadStatus.Failed,error="offline"));val result=DownloadMachine.retry(items);assertEquals(items[0],result[0]);assertEquals(DownloadStatus.Queued,result[1].status);assertNull(result[1].error)}
    @Test fun duplicateSongsHaveIndependentQueueIds() {val a=QueueEntry(track=tracks[0]);val b=QueueEntry(track=tracks[0]);assertNotEquals(a.id,b.id);assertEquals(a.track,b.track)}
}
