package com.battleon.solo

import com.battleon.solo.chapter1.C1M01
import com.battleon.solo.chapter1.C1M02
import com.battleon.solo.chapter1.C1M03
import com.battleon.solo.chapter1.C1M04
import com.battleon.solo.chapter1.C1M05
import com.battleon.solo.chapter1.C1M06
import com.battleon.solo.chapter1.C1M07
import com.battleon.solo.chapter1.C1M08
import com.battleon.solo.chapter1.C1M09
import com.battleon.solo.chapter1.C1M10
import com.battleon.solo.chapter1.C1M11
import com.battleon.solo.chapter1.C1M12
import com.battleon.solo.chapter1.C1M13
import com.battleon.solo.chapter1.C1M14
import com.battleon.solo.chapter1.C1M15
import com.battleon.solo.chapter1.C1M16
import com.battleon.solo.chapter1.C1M17
import com.battleon.solo.chapter1.C1M18
import com.battleon.solo.chapter1.C1M19
import com.battleon.solo.chapter1.C1M20
import com.battleon.solo.chapter2.C2M21
import com.battleon.solo.chapter2.C2M22
import com.battleon.solo.chapter2.C2M23
import com.battleon.solo.chapter2.C2M24
import com.battleon.solo.chapter2.C2M25
import com.battleon.solo.chapter2.C2M26
import com.battleon.solo.chapter2.C2M27
import com.battleon.solo.chapter2.C2M28
import com.battleon.solo.chapter2.C2M29
import com.battleon.solo.chapter2.C2M30

object SoloMissionCatalog {

    private val missions: Map<String, SoloMissionDefinition> = listOf(
        C1M01,
        C1M02,
        C1M03,
        C1M04,
        C1M05,
        C1M06,
        C1M07,
        C1M08,
        C1M09,
        C1M10,
        C1M11,
        C1M12,
        C1M13,
        C1M14,
        C1M15,
        C1M16,
        C1M17,
        C1M18,
        C1M19,
        C1M20,

        C2M21,
        //C2M22,
        C2M23,
        C2M24,
        C2M25,
        C2M26,
        C2M27,
        C2M28,
        C2M29,
        C2M30,

        ).associateBy { it.id }

    fun findMission(
        missionId: String
    ): SoloMissionDefinition? {
        return missions[missionId]
    }
}