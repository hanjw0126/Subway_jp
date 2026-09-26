package io.github.jpsubway.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OdptTrainTimetable(
    @SerialName("owl:sameAs") val id: String = "",
    @SerialName("odpt:railway") val railway: String = "",
    @SerialName("odpt:railDirection") val railDirection: String? = null,
    @SerialName("odpt:trainNumber") val trainNumber: String = "",
    @SerialName("odpt:trainType") val trainType: String? = null,
    @SerialName("odpt:destinationStation") val destinationStation: List<String>? = null,
    @SerialName("odpt:trainTimetableObject") val objects: List<OdptTimetableObject> = emptyList(),
)

@Serializable
data class OdptTimetableObject(
    @SerialName("odpt:departureTime") val departureTime: String? = null,
    @SerialName("odpt:departureStation") val departureStation: String? = null,
    @SerialName("odpt:arrivalTime") val arrivalTime: String? = null,
    @SerialName("odpt:arrivalStation") val arrivalStation: String? = null,
)

@Serializable
data class OdptTrain(
    @SerialName("odpt:railway") val railway: String = "",
    @SerialName("odpt:trainNumber") val trainNumber: String = "",
    @SerialName("odpt:delay") val delay: Int? = null,
    @SerialName("odpt:fromStation") val fromStation: String? = null,
    @SerialName("odpt:toStation") val toStation: String? = null,
    @SerialName("odpt:railDirection") val railDirection: String? = null,
)

@Serializable
data class OdptTrainInformation(
    @SerialName("odpt:railway") val railway: String? = null,
    @SerialName("odpt:operator") val operator: String? = null,
    @SerialName("odpt:trainInformationText") val text: Map<String, String>? = null,
)
