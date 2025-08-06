package com.habittracker.domain.model

import kotlinx.datetime.Clock
import org.junit.Test
import org.junit.Assert.*

class HabitValidationTest {

    private fun createValidHabit(): Habit = Habit(
        id = "test-id",
        title = "Test Habit",
        description = "Test description",
        icon = "🎯",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        reminderEnabled = false,
        targetValue = 1,
        unit = "раз",
        createdBy = "user-1",
        participants = emptyList(),
        maxParticipants = 10,
        inviteCode = null,
        isPublic = false,
        isActive = true,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    @Test
    fun `validateHabit should return valid for correct habit`() {
        val habit = createValidHabit()
        val result = HabitValidation.validateHabit(habit)
        
        assertTrue("Valid habit should pass validation", result.isValid)
        assertTrue("Valid habit should have no errors", result.errors.isEmpty())
    }

    @Test
    fun `validateHabit should fail for empty title`() {
        val habit = createValidHabit().copy(title = "")
        val result = HabitValidation.validateHabit(habit)
        
        assertFalse("Empty title should fail validation", result.isValid)
        assertTrue("Should have title error", result.hasError(HabitValidation.HabitField.TITLE))
        assertEquals("Should have correct error code", "TITLE_EMPTY", result.getError(HabitValidation.HabitField.TITLE)?.code)
    }

    @Test
    fun `validateHabit should fail for too long title`() {
        val longTitle = "a".repeat(HabitValidation.MAX_TITLE_LENGTH + 1)
        val habit = createValidHabit().copy(title = longTitle)
        val result = HabitValidation.validateHabit(habit)
        
        assertFalse("Too long title should fail validation", result.isValid)
        assertTrue("Should have title error", result.hasError(HabitValidation.HabitField.TITLE))
        assertEquals("Should have correct error code", "TITLE_TOO_LONG", result.getError(HabitValidation.HabitField.TITLE)?.code)
    }

    @Test
    fun `validateHabit should fail for too long description`() {
        val longDescription = "a".repeat(HabitValidation.MAX_DESCRIPTION_LENGTH + 1)
        val habit = createValidHabit().copy(description = longDescription)
        val result = HabitValidation.validateHabit(habit)
        
        assertFalse("Too long description should fail validation", result.isValid)
        assertTrue("Should have description error", result.hasError(HabitValidation.HabitField.DESCRIPTION))
        assertEquals("Should have correct error code", "DESCRIPTION_TOO_LONG", result.getError(HabitValidation.HabitField.DESCRIPTION)?.code)
    }

    @Test
    fun `validateHabit should fail for invalid color format`() {
        val habit = createValidHabit().copy(color = "invalid-color")
        val result = HabitValidation.validateHabit(habit)
        
        assertFalse("Invalid color should fail validation", result.isValid)
        assertTrue("Should have color error", result.hasError(HabitValidation.HabitField.COLOR))
        assertEquals("Should have correct error code", "COLOR_INVALID_FORMAT", result.getError(HabitValidation.HabitField.COLOR)?.code)
    }

    @Test
    fun `validateHabit should accept valid color formats`() {
        val validColors = listOf("#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF")
        
        validColors.forEach { color ->
            val habit = createValidHabit().copy(color = color)
            val result = HabitValidation.validateHabit(habit)
            
            assertFalse("Color $color should be valid", result.hasError(HabitValidation.HabitField.COLOR))
        }
    }

    @Test
    fun `validateHabit should fail for invalid target value`() {
        val habit1 = createValidHabit().copy(targetValue = 0)
        val result1 = HabitValidation.validateHabit(habit1)
        
        assertFalse("Zero target value should fail validation", result1.isValid)
        assertTrue("Should have target value error", result1.hasError(HabitValidation.HabitField.TARGET_VALUE))
        assertEquals("Should have correct error code", "TARGET_VALUE_TOO_LOW", result1.getError(HabitValidation.HabitField.TARGET_VALUE)?.code)

        val habit2 = createValidHabit().copy(targetValue = HabitValidation.MAX_TARGET_VALUE + 1)
        val result2 = HabitValidation.validateHabit(habit2)
        
        assertFalse("Too high target value should fail validation", result2.isValid)
        assertTrue("Should have target value error", result2.hasError(HabitValidation.HabitField.TARGET_VALUE))
        assertEquals("Should have correct error code", "TARGET_VALUE_TOO_HIGH", result2.getError(HabitValidation.HabitField.TARGET_VALUE)?.code)
    }

    @Test
    fun `validateHabit should fail for empty unit`() {
        val habit = createValidHabit().copy(unit = "")
        val result = HabitValidation.validateHabit(habit)
        
        assertFalse("Empty unit should fail validation", result.isValid)
        assertTrue("Should have unit error", result.hasError(HabitValidation.HabitField.UNIT))
        assertEquals("Should have correct error code", "UNIT_EMPTY", result.getError(HabitValidation.HabitField.UNIT)?.code)
    }

    @Test
    fun `validateHabit should validate group habit constraints`() {
        val groupHabit = createValidHabit().copy(
            type = HabitType.GROUP,
            maxParticipants = 1 // Too low for group habit
        )
        val result = HabitValidation.validateHabit(groupHabit)
        
        assertFalse("Group habit with too few max participants should fail", result.isValid)
        assertTrue("Should have max participants error", result.hasError(HabitValidation.HabitField.MAX_PARTICIPANTS))
        assertEquals("Should have correct error code", "MAX_PARTICIPANTS_TOO_LOW", result.getError(HabitValidation.HabitField.MAX_PARTICIPANTS)?.code)
    }

    @Test
    fun `validateHabit should validate invite code format`() {
        val groupHabit = createValidHabit().copy(
            type = HabitType.GROUP,
            inviteCode = "invalid-code"
        )
        val result = HabitValidation.validateHabit(groupHabit)
        
        assertFalse("Group habit with invalid invite code should fail", result.isValid)
        assertTrue("Should have invite code error", result.hasError(HabitValidation.HabitField.INVITE_CODE))
        assertEquals("Should have correct error code", "INVITE_CODE_INVALID_FORMAT", result.getError(HabitValidation.HabitField.INVITE_CODE)?.code)
    }

    @Test
    fun `validateHabit should validate participants count`() {
        val tooManyParticipants = (1..15).map { "user-$it" }
        val groupHabit = createValidHabit().copy(
            type = HabitType.GROUP,
            maxParticipants = 10,
            participants = tooManyParticipants
        )
        val result = HabitValidation.validateHabit(groupHabit)
        
        assertFalse("Group habit with too many participants should fail", result.isValid)
        assertTrue("Should have participants error", result.hasError(HabitValidation.HabitField.PARTICIPANTS))
        assertEquals("Should have correct error code", "TOO_MANY_PARTICIPANTS", result.getError(HabitValidation.HabitField.PARTICIPANTS)?.code)
    }

    @Test
    fun `generateInviteCode should create valid code`() {
        val code = HabitValidation.generateInviteCode()
        
        assertEquals("Code should have correct length", HabitValidation.INVITE_CODE_LENGTH, code.length)
        assertTrue("Code should match regex pattern", HabitValidation.INVITE_CODE_REGEX.matches(code))
    }

    @Test
    fun `isValidInviteCode should validate codes correctly`() {
        assertTrue("Valid code should pass", HabitValidation.isValidInviteCode("ABCD1234"))
        assertFalse("Too short code should fail", HabitValidation.isValidInviteCode("ABC123"))
        assertFalse("Too long code should fail", HabitValidation.isValidInviteCode("ABCD12345"))
        assertFalse("Lowercase code should fail", HabitValidation.isValidInviteCode("abcd1234"))
        assertFalse("Code with special chars should fail", HabitValidation.isValidInviteCode("ABCD-123"))
    }

    @Test
    fun `isValidHabit should be shortcut for validation`() {
        val validHabit = createValidHabit()
        val invalidHabit = createValidHabit().copy(title = "")
        
        assertTrue("Valid habit should return true", HabitValidation.isValidHabit(validHabit))
        assertFalse("Invalid habit should return false", HabitValidation.isValidHabit(invalidHabit))
    }

    @Test
    fun `validation should handle multiple errors`() {
        val invalidHabit = createValidHabit().copy(
            title = "", // Empty title
            color = "invalid", // Invalid color
            targetValue = 0, // Invalid target value
            unit = "" // Empty unit
        )
        val result = HabitValidation.validateHabit(invalidHabit)
        
        assertFalse("Multiple invalid fields should fail validation", result.isValid)
        assertEquals("Should have 4 errors", 4, result.errors.size)
        assertTrue("Should have title error", result.hasError(HabitValidation.HabitField.TITLE))
        assertTrue("Should have color error", result.hasError(HabitValidation.HabitField.COLOR))
        assertTrue("Should have target value error", result.hasError(HabitValidation.HabitField.TARGET_VALUE))
        assertTrue("Should have unit error", result.hasError(HabitValidation.HabitField.UNIT))
    }
}
