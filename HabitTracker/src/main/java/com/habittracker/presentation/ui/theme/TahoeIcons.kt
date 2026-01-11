package com.habittracker.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.habittracker.R

/**
 * Tahoe Design System Icons
 * Кастомные иконки в стиле Heroicons для Tahoe темы
 */
object TahoeIcons {
    
    // Navigation
    val ArrowBack: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_arrow_back)
    
    val ChevronRight: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_chevron_right)
    
    val ChevronLeft: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_chevron_left)
    
    val ChevronDown: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_chevron_down)
    
    val Home: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_home)
    
    // Actions
    val Plus: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_plus)
    
    val Check: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_check)
    
    val CheckCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_check_circle)
    
    val CheckBadge: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_check_badge)
    
    val XMark: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_x_mark)
    
    val XCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_x_circle)
    
    val Trash: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_trash)
    
    val Pencil: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_pencil)
    
    val Share: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_share)
    
    val Clipboard: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_clipboard)
    
    val ArrowPath: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_arrow_path)
    
    // Users & Social
    val User: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_user)
    
    val UserPlus: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_user_plus)
    
    val UserMinus: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_user_minus)
    
    val UserGroup: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_user_group)
    
    val Users: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_users)
    
    val Group: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_group)
    
    // Time & Calendar
    val Clock: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_clock)
    
    val Timer: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_timer)
    
    val ClockHistory: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_clock_history)
    
    val Calendar: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_calendar)
    
    val CalendarDays: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_calendar_days)
    
    val Hourglass: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_hourglass)
    
    // Notifications
    val Bell: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_bell)
    
    val BellSlash: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_bell_slash)
    
    // Status & Feedback
    val Warning: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_warning)
    
    val ExclamationCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_exclamation_circle)
    
    val Info: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_info)
    
    // Visibility
    val Eye: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_eye)
    
    val EyeSlash: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_eye_slash)
    
    // Settings & Tools
    val Cog: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_cog)
    
    val EllipsisVertical: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_ellipsis_vertical)
    
    val Funnel: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_funnel)
    
    val Bars3: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_bars_3)
    
    // Communication
    val Envelope: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_envelope)
    
    val PaperAirplane: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_paper_airplane)
    
    // Search
    val MagnifyingGlass: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_magnifying_glass)
    
    // Security
    val LockClosed: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_lock_closed)
    
    val QrCode: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_qr_code)
    
    // Stats & Charts
    val ChartBar: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_chart_bar)
    
    val TrendingUp: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_trending_up)
    
    // Achievements & Rewards
    val Trophy: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_trophy)
    
    val Star: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_star)
    
    val Flag: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_flag)
    
    val Fire: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_fire)
    
    val Sparkles: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_sparkles)
    
    val Bolt: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_bolt)
    
    // Categories & Organization
    val Tag: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_tag)
    
    val Squares2x2: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_squares_2x2)
    
    val Swatch: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_swatch)
    
    // Documents
    val DocumentText: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_document_text)
    
    // Discovery & Exploration
    val Globe: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_globe)
    
    val Compass: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_compass)
    
    // Time of Day
    val Sun: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_sun)
    
    val Moon: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_moon)
    
    // Education & Learning
    val AcademicCap: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_academic_cap)
    
    val BookOpen: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_book_open)
    
    // Media
    val Camera: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_camera)
    
    // Misc
    val Heart: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_heart)
    
    val Circle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_tahoe_circle)
}
