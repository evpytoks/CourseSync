using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class GradingScoresPerUser : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql("DELETE FROM course_grading_scores;");

            migrationBuilder.DropIndex(
                name: "IX_course_grading_scores_course_grading_element_id_number",
                table: "course_grading_scores");

            migrationBuilder.DropColumn(
                name: "count",
                table: "course_grading_elements");

            migrationBuilder.AddColumn<Guid>(
                name: "user_id",
                table: "course_grading_scores",
                type: "uuid",
                nullable: false);

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_scores_user_id",
                table: "course_grading_scores",
                column: "user_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_scores_user_id_course_grading_element_id_num~",
                table: "course_grading_scores",
                columns: new[] { "user_id", "course_grading_element_id", "number" },
                unique: true);

            migrationBuilder.AddForeignKey(
                name: "FK_course_grading_scores_users_user_id",
                table: "course_grading_scores",
                column: "user_id",
                principalTable: "users",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_course_grading_scores_users_user_id",
                table: "course_grading_scores");

            migrationBuilder.DropIndex(
                name: "IX_course_grading_scores_user_id",
                table: "course_grading_scores");

            migrationBuilder.DropIndex(
                name: "IX_course_grading_scores_user_id_course_grading_element_id_num~",
                table: "course_grading_scores");

            migrationBuilder.DropColumn(
                name: "user_id",
                table: "course_grading_scores");

            migrationBuilder.AddColumn<int>(
                name: "count",
                table: "course_grading_elements",
                type: "integer",
                nullable: false,
                defaultValue: 1);

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_scores_course_grading_element_id_number",
                table: "course_grading_scores",
                columns: new[] { "course_grading_element_id", "number" },
                unique: true);
        }
    }
}
