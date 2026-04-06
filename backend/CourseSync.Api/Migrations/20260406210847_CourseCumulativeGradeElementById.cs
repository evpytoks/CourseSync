using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    public partial class CourseCumulativeGradeElementById : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "course_grading_element_id",
                table: "course_cumulative_grade_elements",
                type: "uuid",
                nullable: true);

            migrationBuilder.Sql(
                """
                UPDATE course_cumulative_grade_elements AS e
                SET course_grading_element_id = cge.id
                FROM course_grading_elements AS cge
                WHERE cge.course_id = e.course_id AND cge.name = e.element_name;
                """);

            migrationBuilder.Sql(
                "DELETE FROM course_cumulative_grade_elements WHERE course_grading_element_id IS NULL;");

            migrationBuilder.DropColumn(
                name: "element_name",
                table: "course_cumulative_grade_elements");

            migrationBuilder.AlterColumn<Guid>(
                name: "course_grading_element_id",
                table: "course_cumulative_grade_elements",
                type: "uuid",
                nullable: false,
                oldClrType: typeof(Guid),
                oldType: "uuid",
                oldNullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_course_cumulative_grade_elements_course_grading_element_id",
                table: "course_cumulative_grade_elements",
                column: "course_grading_element_id");

            migrationBuilder.AddForeignKey(
                name: "FK_course_cumulative_grade_elements_course_grading_elements_co~",
                table: "course_cumulative_grade_elements",
                column: "course_grading_element_id",
                principalTable: "course_grading_elements",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);
        }

        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_course_cumulative_grade_elements_course_grading_elements_co~",
                table: "course_cumulative_grade_elements");

            migrationBuilder.DropIndex(
                name: "IX_course_cumulative_grade_elements_course_grading_element_id",
                table: "course_cumulative_grade_elements");

            migrationBuilder.AddColumn<string>(
                name: "element_name",
                table: "course_cumulative_grade_elements",
                type: "character varying(50)",
                maxLength: 50,
                nullable: false,
                defaultValue: "");

            migrationBuilder.Sql(
                """
                UPDATE course_cumulative_grade_elements AS e
                SET element_name = cge.name
                FROM course_grading_elements AS cge
                WHERE cge.id = e.course_grading_element_id;
                """);

            migrationBuilder.DropColumn(
                name: "course_grading_element_id",
                table: "course_cumulative_grade_elements");
        }
    }
}
