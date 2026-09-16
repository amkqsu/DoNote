package `fun`.dogon.note

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0D0D0E)
val SurfaceColor = Color(0xFF19191B)
val Raised = Color(0xFF272729)
val Line = Color(0xFF303034)
val Muted = Color(0xFF99999F)
val Accent = Color(0xFFC7C7DB)
fun hexColor(s: String) = Color(android.graphics.Color.parseColor(s))
fun contrast(c: Color) = if((c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f) > 0.6f) Color(0xFF20202B) else Color.White
val palette = listOf("#c7c7db","#f5f5f7","#91cea6","#edc766","#ef9096","#90bdf0","#b49cf0","#edac7f","#65cabe","#e78fbf")
@Composable fun NoteTheme(accent: String = "#c7c7db",content: @Composable () -> Unit) {
    val a = hexColor(if(validColor(accent)) accent else "#c7c7db")
    MaterialTheme(colorScheme = darkColorScheme(primary = a,onPrimary = contrast(a),secondary = a,background = Bg,onBackground = Color(0xFFF5F5F7),surface = SurfaceColor,onSurface = Color(0xFFF5F5F7),surfaceVariant = Raised,onSurfaceVariant = Muted,outline = Line,error = Color(0xFFEF9096)),shapes = Shapes(small = RoundedCornerShape(13.dp),medium = RoundedCornerShape(15.dp),large = RoundedCornerShape(22.dp)),typography = Typography(headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif,fontSize = 29.sp,fontWeight = FontWeight.Bold,letterSpacing = (-1.4).sp),headlineSmall = TextStyle(fontSize = 24.sp,fontWeight = FontWeight.SemiBold,letterSpacing = (-0.7).sp),titleMedium = TextStyle(fontSize = 18.sp,fontWeight = FontWeight.SemiBold),bodyMedium = TextStyle(fontSize = 14.sp),labelSmall = TextStyle(fontSize = 12.sp)),content = content)
}
@Composable fun Panel(modifier: Modifier = Modifier,content: @Composable ColumnScope.() -> Unit) { Surface(modifier = modifier.fillMaxWidth(),color = SurfaceColor,shape = RoundedCornerShape(22.dp),border = BorderStroke(1.dp,Line)) { Column(Modifier.padding(17.dp),verticalArrangement = Arrangement.spacedBy(12.dp),content = content) } }

