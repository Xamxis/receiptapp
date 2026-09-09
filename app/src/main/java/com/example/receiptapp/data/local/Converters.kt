package com.example.receiptapp.data.local

import androidx.room.TypeConverter
import com.example.receiptapp.data.model.Category
import com.example.receiptapp.data.model.NutritionTag
import java.time.LocalDate

/** Room-TypeConverter für Enums, LocalDate und List<String> (Tags). */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun fromCategory(category: Category): String = category.name

    @TypeConverter
    fun toCategory(value: String): Category = Category.fromKeyOrNull(value) ?: Category.SONSTIGES

    @TypeConverter
    fun fromNutritionTag(tag: NutritionTag?): String? = tag?.name

    @TypeConverter
    fun toNutritionTag(value: String?): NutritionTag? = NutritionTag.fromKeyOrNull(value)

    @TypeConverter
    fun fromTagList(tags: List<String>): String = tags.joinToString(separator = "|")

    @TypeConverter
    fun toTagList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split("|")
}
