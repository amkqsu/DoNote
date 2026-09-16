package `fun`.dogon.note

import android.graphics.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

val iconKeys=listOf("note","work","idea","person","cart","heart","book","star","home","music","travel","code","gift","calendar","check","coffee","fitness","money","key","flag","pet","sun","moon","plant")
val iconLabels=listOf("Not","İş","Fikir","Kişisel","Alışveriş","Kalp","Kitap","Yıldız","Ev","Müzik","Seyahat","Kod","Hediye","Takvim","Görev","Kahve","Spor","Para","Anahtar","Bayrak","Hayvan","Güneş","Ay","Doğa")
fun noteIcon(key:String):ImageVector=when(key) {
    "work"->Icons.Outlined.WorkOutline;"idea"->Icons.Outlined.Lightbulb;"person"->Icons.Outlined.PersonOutline;"cart"->Icons.Outlined.ShoppingCart;"heart"->Icons.Outlined.FavoriteBorder;"book"->Icons.Outlined.MenuBook;"star"->Icons.Outlined.StarOutline;"home"->Icons.Outlined.Home;"music"->Icons.Outlined.MusicNote;"travel"->Icons.Outlined.Flight;"code"->Icons.Outlined.Code;"gift"->Icons.Outlined.CardGiftcard;"calendar"->Icons.Outlined.CalendarMonth;"check"->Icons.Outlined.CheckCircleOutline;"coffee"->Icons.Outlined.Coffee;"fitness"->Icons.Outlined.FitnessCenter;"money"->Icons.Outlined.Payments;"key"->Icons.Outlined.Key;"flag"->Icons.Outlined.Flag;"pet"->Icons.Outlined.Pets;"sun"->Icons.Outlined.WbSunny;"moon"->Icons.Outlined.Bedtime;"plant"->Icons.Outlined.Eco;else->Icons.Outlined.Description
}
fun iconBitmap(key:String,color:String):Bitmap {
    val res=Repo.context.resources.getIdentifier("ic_$key","drawable",Repo.context.packageName).takeIf { it!=0 }?:R.drawable.ic_note
    val drawable=Repo.context.getDrawable(res)!!.mutate();drawable.setTint(Color.parseColor(color));val b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);drawable.setBounds(8,8,88,88);drawable.draw(Canvas(b));return b
}
