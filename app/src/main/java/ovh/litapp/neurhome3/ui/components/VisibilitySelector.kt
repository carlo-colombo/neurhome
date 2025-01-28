package ovh.litapp.neurhome3.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ovh.litapp.neurhome3.data.ApplicationVisibility

@Composable
@Preview
fun VisibilitySelector(
    modifier: Modifier = Modifier,
    visibility: ApplicationVisibility = ApplicationVisibility.VISIBLE,
    select: (ApplicationVisibility) -> Unit = {}
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ApplicationVisibility.entries.forEachIndexed { index, applicationVisibility ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ApplicationVisibility.entries.size
                ),
                selected = applicationVisibility == visibility,
                onClick = { select(applicationVisibility) },
                label = @Composable {
                    Icon(
                        imageVector = applicationVisibility.imageVector,
                        contentDescription = applicationVisibility.description
                    )
                }
            )
        }
    }
}