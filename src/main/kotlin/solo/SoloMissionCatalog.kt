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
        ).associateBy { it.id }

    fun findMission(
        missionId: String
    ): SoloMissionDefinition? {
        return missions[missionId]
    }
}